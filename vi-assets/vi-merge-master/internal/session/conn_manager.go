package session

import (
	"sync"
	"time"
	"vi-merge/internal/notify"
	"vi-merge/internal/pkg/log"

	"github.com/gorilla/websocket"
)

type ConnectedData struct {
	wsCon     *websocket.Conn
	diskRw    bool
	noticeUrl string
}

// ConnManager 连接管理器，管理所有活跃的WebSocket连接
type ConnManager struct {
	connections map[string]*ConnectedData // 连接ID -> 连接实例
	diskRwCnt   int
	mu          sync.RWMutex // 读写锁，保证并发安全
}

// 单例模式
var globalConnManager = &ConnManager{
	connections: make(map[string]*ConnectedData),
}

// GetConnManager 获取连接管理器实例
func GetConnManager() *ConnManager {
	return globalConnManager
}

func (cm *ConnManager) AddConn(connID string, notify string, conn *websocket.Conn) {
	cm.mu.Lock()
	defer cm.mu.Unlock()
	connectedData := &ConnectedData{
		wsCon:     conn,
		diskRw:    false,
		noticeUrl: notify,
	}
	cm.connections[connID] = connectedData
}

func (cm *ConnManager) RemoveConn(connID string) {
	cm.mu.Lock()
	defer cm.mu.Unlock()
	if conn, ok := cm.connections[connID]; ok {
		conn.wsCon.Close()
		if conn.diskRw {
			cm.diskRwCnt--
		}
		delete(cm.connections, connID)
	}
}

func (cm *ConnManager) GetConn(connID string) (*websocket.Conn, bool) {
	cm.mu.RLock()
	defer cm.mu.RUnlock()
	conn, ok := cm.connections[connID]
	if conn == nil {
		return nil, false
	}
	return conn.wsCon, ok
}

func (cm *ConnManager) ConnDiskRw(connID string) bool {
	cm.mu.RLock()
	defer cm.mu.RUnlock()
	conn, ok := cm.connections[connID]
	if ok {
		conn.diskRw = true
		cm.diskRwCnt++
		return true
	}
	return false
}

func (cm *ConnManager) GetAllConns() map[string]*websocket.Conn {
	cm.mu.RLock()
	defer cm.mu.RUnlock()
	conns := make(map[string]*websocket.Conn, len(cm.connections))
	for k, v := range cm.connections {
		conns[k] = v.wsCon
	}
	return conns
}

func (cm *ConnManager) GetConnSizeSafe() int {
	cm.mu.RLock()
	defer cm.mu.RUnlock()
	return len(cm.connections)
}

func (cm *ConnManager) GetSize() int {
	//不加锁，数据不要求完整精准
	//cm.mu.RLock()
	//defer cm.mu.RUnlock()
	return len(cm.connections)
}

func (cm *ConnManager) GetDiskRw() int {
	return cm.diskRwCnt
}

func (cm *ConnManager) Stop() {
	cm.mu.RLock()
	defer cm.mu.RUnlock()

	// 发送断开消息，然后等待上游断开
	for k, v := range cm.connections {
		// 发送断开消息
		if v.wsCon != nil {
			log.WarnK(k, "send notice close message,url=%v", v.noticeUrl)
			notify.HttpNotifyMedia(k, v.noticeUrl, "con stop")
			// v.wsCon.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
			time.Sleep(time.Millisecond * 100)
		}
	}

}
