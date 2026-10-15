package wshandler

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"time"
	"vi-merge/conf"
	"vi-merge/internal/metrics"
	"vi-merge/internal/models"
	"vi-merge/internal/notify/wsnotify"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/processor"
	"vi-merge/internal/service"
	"vi-merge/internal/session"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

const (
	READ_DEALLINE_TIME = 60

	PONG_READ_DEALLINE_TIME = 60

	WS_READ_LIMIT = 1024 * 1024
)

var (
	SessionOverMax = errors.New("session over max session")
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  2048,
	WriteBufferSize: 2048,
	CheckOrigin: func(r *http.Request) bool {
		if r.Method != http.MethodGet {
			return false
		}
		return true
	},
}

// ConnHandler 适配Gin的连接处理器
type ConnHandler struct {
	router    *BusinessRouter
	wsService *service.WsService
	connMgr   *session.ConnManager
	notifier  *wsnotify.ErrorNotifier

	parserMgr *models.ParserManager
}

func NewConnHandler() *ConnHandler {
	models.InitParserManager()

	wsService := service.NewWsService()
	return &ConnHandler{
		router:    NewBusinessRouter(wsService),
		wsService: wsService,
		connMgr:   session.GetConnManager(),
		notifier:  wsnotify.GetErrorNotifier(),
	}
}

func (h *ConnHandler) AudioPcmWs(c *gin.Context) {
	bizCtx, err := models.ParseBusinessHeader(c.Request.Header)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		metrics.MergeWsFailInc(models.MergeWav, err.Error())
		return
	}

	sessionId := bizCtx.BizConfig.SessionId
	log.InfoK(sessionId, "BizConfig=%v,notify=%v", bizCtx.BizConfig.String(), bizCtx.Notice)

	if h.connMgr.GetConnSizeSafe() > conf.App.MaxSession {
		log.WarnK(sessionId, "[ConnHandler],ConnSize,max=%v,err:%v", conf.App.MaxSession, SessionOverMax)
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": SessionOverMax.Error()})
		metrics.MergeWsFailInc(models.MergeWav, SessionOverMax.Error())
		return
	}

	//升级WebSocket连接
	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		log.WarnK(sessionId, "[ConnHandler],Upgrade,err:%v", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "websocket upgrade failed"})
		metrics.MergeWsFailInc(models.MergeWav, "upgrade failed")
		return
	}

	if len(sessionId) < 1 {
		if sessionId == "" {
			sessionId = fmt.Sprintf("%s_%d", "merge", time.Now().UnixNano())
		}
	}

	// 设置单次读取消息最大字节上限,限制 websocket 单条消息最大长度，超过该值直接返回错误、断开连接，防止超大报文内存攻击
	// 超过限制之后会发生 ——ReadMessage 返回错误 websocket.ErrReadLimit。连接会被标记错误，后续读取持续失败；建议收到错误直接 close 连接
	conn.SetReadLimit(WS_READ_LIMIT)

	h.connMgr.AddConn(sessionId, bizCtx.Notice, conn)
	log.InfoK(sessionId, "[ConnHandler],AddConn,BusinessType=%s", bizCtx.BusinessType)

	//启动读写协程
	go h.readPump(sessionId, conn, bizCtx)

	//发ping,20260605屏蔽
	//go h.writePump(sessionId, conn)
}

// readPump 读取消息
func (h *ConnHandler) readPump(sID string, conn *websocket.Conn, bizHeader *models.BusinessHeader) {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(sID, "[ConnHandler]readPump,defer,v=%v", v)
		}
		h.connMgr.RemoveConn(sID)
		log.InfoK(sID, "[ConnHandler]readPump,defer,end,RemoveConn")
	}()

	//conn.SetReadDeadline(time.Now().Add(READ_DEALLINE_TIME * time.Second))

	//设置不发ping，这里就不会有pong的响应
	//conn.SetPongHandler(func(s string) error {
	//	log.InfoK(sID, "[ConnHandler]PongHandler,str=%v", s)
	//	conn.SetReadDeadline(time.Now().Add(PONG_READ_DEALLINE_TIME * time.Second))
	//	return nil
	//})

	//处理器
	wsPcmProcessor := processor.NewWsPcmProcessor()
	defer func() {
		if wsPcmProcessor != nil {
			wsPcmProcessor.Close()
		}
	}()
	msgParser, err := h.router.MessageParser(bizHeader.BusinessType)
	if err != nil {
		log.WarnK(sID, "RouteHandler,GetParser,err=%s", err)
		conn.WriteMessage(websocket.TextMessage, json.RawMessage(`{"code":"-1000","message":"BusinessType Invalid"}`))
		return
	}

	//通知可以开始发流
	err = conn.WriteMessage(websocket.TextMessage, json.RawMessage(`{"code":"0000","message":"Wait..."}`))
	if err != nil {
		log.WarnK(sID, "RouteHandler,text,0000 fail,err=%s", err)
	}

	var wsMessageType int
	var data []byte

	//count := 0
	// 循环读消息。这里不主动关闭，等待上游关闭
	for {
		err = nil
		data = nil

		// better to set read deadline,then reading message immediately
		if err = conn.SetReadDeadline(time.Now().Add(READ_DEALLINE_TIME * time.Second)); err != nil {
			log.WarnK(sID, "ConnHandler:set conn read deadline err=%s", err)
		}

		wsMessageType, data, err = conn.ReadMessage()
		if err != nil {
			log.WarnK(sID, "[ConnHandler]ReadMessage,err=%v", err)
			/***
			  正常状态：只有 1000 是正常关闭，1001/1012/1013 是 “友好的主动关闭”（非异常）；
			  常见异常：1006（网络 / 进程崩溃）、1009（消息过大）、1011（服务端内部错误）是开发中最常遇到的异常状态码；
			  伪状态码：1005/1006/1015 是本地标识的状态码，不会从对方的关闭帧中收到，其余状态码均为协议定义的 “可传输” 状态码。
			*/
			//if websocket.IsCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure, websocket.CloseAbnormalClosure) {
			//	log.WarnK(sID, "[ConnHandler]ReadMessage,Break,err=%v", err)

			//清理通知，统计最后一次合音。本协程退出，子协程可以继续执行
			if wsPcmProcessor != nil {
				wsPcmProcessor.Flush(models.EVENT_END_INTERNAL)
			}
			break
			//}
		}

		//log.DebugK(sID, "[ConnHandler]Read,count=%v,len=%v", count, len(data))
		//if (len(data)-1)%32 != 0 {
		//	log.DebugK(sID, "[ConnHandler]Read,count=%v,data=%v", count, data)
		//}
		//count++

		msgType, ok := models.MessageTypeMap[wsMessageType]
		if !ok {
			log.WarnK(sID, "[ConnHandler]MessageTypeMap,Fail,MessageType=%v", wsMessageType)
		}

		bizMsg := &models.BusinessMessage{
			BusinessHeader: bizHeader,
			ProType:        models.MergeWav,
			MsgType:        msgType,
			Content:        data,
			SessionId:      sID,
		}

		//路由到处理器
		if err = h.router.RouteHandler(sID, wsPcmProcessor, msgParser, bizMsg); err != nil {
			log.WarnK(sID, "[ConnHandler]RouteHandler,err=%v", err)
		}

		//设置超时
		//conn.SetReadDeadline(time.Now().Add(READ_DEALLINE_TIME * time.Second))
	}
}

// writePump 发送心跳。
func (h *ConnHandler) writePump(sID string, conn *websocket.Conn) {
	ticker := time.NewTicker(30 * time.Second)

	defer func() {
		ticker.Stop()
		conn.Close()

		if v := recover(); v != nil {
			log.WarnK(sID, "[ConnHandler]writePump,v=%v", v)
		}
	}()

	for {
		select {
		case <-ticker.C:
			if conn == nil {
				log.WarnK(sID, "[ConnHandler]conn discon")
				return
			}
			if err := conn.WriteControl(websocket.PingMessage, []byte{}, time.Now().Add(5*time.Second)); err != nil {
				log.WarnK(sID, "[ConnHandler]PingMessage,fail")
				return
			}
		}
	}
}
