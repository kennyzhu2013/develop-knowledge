package wshandler

import (
	"fmt"
	"vi-merge/internal/models"
	"vi-merge/internal/notify/wsnotify"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/processor"
	"vi-merge/internal/service"
)

type BusinessRouter struct {
	//processors map[string]map[string]processor.BusinessProcessor
	wsService *service.WsService
	parserMgr *models.ParserManager
	notifier  *wsnotify.ErrorNotifier // 注入异常通知器
}

func NewBusinessRouter(wsService *service.WsService) *BusinessRouter {
	router := &BusinessRouter{
		//processors: make(map[string]map[string]processor.BusinessProcessor),
		wsService: wsService,
		parserMgr: models.GetGlobalParserManager(),
		notifier:  wsnotify.GetErrorNotifier(),
		//wsPcmProcessor: make(map[string]*processor.WsPcmProcessor),
	}

	// 注册处理器
	//router.registerProcessor(processor.NewChatSendProcessor(wsService))
	//router.registerProcessor(processor.NewChatHeartbeatProcessor(wsService))
	//merge-实际业务入口
	//router.registerProcessor(processor.NewWsPcmProcessor(wsService))
	return router
}

//func (r *BusinessRouter) registerProcessor(p processor.BusinessProcessor) {
//	bizType := p.GetBusinessType()
//	msgType := p.GetMessageType()
//
//	if _, ok := r.processors[bizType]; !ok {
//		r.processors[bizType] = make(map[string]processor.BusinessProcessor)
//	}
//	r.processors[bizType][msgType] = p
//	log.InfoF("registerProcessor,%s -> %s", bizType, msgType)
//}

func (r *BusinessRouter) RouteHandler(sessionID string, wsPcmProcessor *processor.WsPcmProcessor,
	messageParser models.MessageParser, bizMessage *models.BusinessMessage) error {
	//BusinessTypeMerge，大业务类型，WS建立就确定了类型。
	//bizType := bizMessage.BusinessHeader.BusinessType

	//业务process类型，真业务类型
	pType := bizMessage.ProType
	//消息类型，二进制还是文本
	subMsgType := bizMessage.MsgType

	//解析json类型
	data, err := messageParser.ParseContent(pType, subMsgType, bizMessage.Content)
	if err != nil {
		log.WarnK(sessionID, "RouteHandler,ParseContent,err=%s", err)
		return err
	}

	if err = wsPcmProcessor.Process(sessionID, bizMessage.BusinessHeader, data); err != nil {
		log.WarnK(sessionID, "RouteHandler,Process,err=%s", err)
		wsErr := models.NewWsError(
			models.ErrCodeBizProcessFailed,
			fmt.Sprintf("processor execute failed: %v", err),
			map[string]interface{}{
				"conn_id": sessionID,
				"p_type":  pType,
			},
		)
		return r.notifier.NotifyClient(sessionID, wsErr)
	}
	return nil
}

func (r *BusinessRouter) MessageParser(bizType string) (models.MessageParser, error) {
	//获取解析器并解析消息内容
	return r.parserMgr.GetParser(bizType)
}
