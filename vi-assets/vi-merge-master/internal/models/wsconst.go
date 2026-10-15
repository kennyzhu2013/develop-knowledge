package models

import "github.com/gorilla/websocket"

const (
	WsTextMessage   = "TextMessage"
	WsBinaryMessage = "BinaryMessage"
	WsCloseMessage  = "CloseMessage"

	WsPingMessage = "PingMessage"
	WsPongMessage = "PongMessage"
)

var MessageTypeMap = map[int]string{
	websocket.TextMessage:   WsTextMessage,   // 文本消息
	websocket.BinaryMessage: WsBinaryMessage, // 二进制消息
	websocket.CloseMessage:  WsCloseMessage,  // 关闭消息
	websocket.PingMessage:   WsPingMessage,   // Ping 消息
	websocket.PongMessage:   WsPongMessage,   // Pong 消息
}
