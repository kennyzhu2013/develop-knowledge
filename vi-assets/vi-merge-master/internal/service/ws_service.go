package service

import (
	"errors"
	"fmt"
	"time"
	"vi-merge/internal/models"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/session"

	"github.com/gorilla/websocket"
)

type WsService struct {
	connManager *session.ConnManager
}

func NewWsService() *WsService {
	return &WsService{
		connManager: session.GetConnManager(),
	}
}

func (s *WsService) SendMessage(sessionId string, msg *models.BusinessMessage) error {
	conn, ok := s.connManager.GetConn(sessionId)
	if !ok {
		return s.logError("con not exist,sId=%s", sessionId)
	}

	data, err := models.EncodeBusinessMessage(msg)
	if err != nil {
		return s.logError("EncodeBusinessMessage fail：%v", err)
	}

	if err = conn.SetWriteDeadline(time.Now().Add(5 * time.Second)); err != nil {
		return s.logError("SetWriteDeadline fail：%v", err)
	}

	if err = conn.WriteMessage(websocket.TextMessage, data); err != nil {
		s.connManager.RemoveConn(sessionId)
		return s.logError("WriteMessage Fail,RemoveConn,err：%v,RemoveConn", sessionId, err)
	}
	return nil
}

func (s *WsService) BroadcastMessage(excludeConnID string, msg *models.BusinessMessage) error {
	allConns := s.connManager.GetAllConns()
	for connID := range allConns {
		if connID == excludeConnID {
			continue
		}
		_ = s.SendMessage(connID, msg)
	}
	return nil
}

func (s *WsService) logError(format string, v ...interface{}) error {
	log.WarnF(format, v...)
	return errors.New(fmt.Sprintf(format, v...))
}
