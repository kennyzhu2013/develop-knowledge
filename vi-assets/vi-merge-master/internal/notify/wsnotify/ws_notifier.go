package wsnotify

import (
	"errors"
	"vi-merge/internal/models"
	"vi-merge/internal/pkg/log"

	"time"
	"vi-merge/internal/session"

	"github.com/gorilla/websocket"
)

type ErrorNotifier struct {
	connManager *session.ConnManager
}

var globalErrorNotifier = &ErrorNotifier{
	connManager: session.GetConnManager(),
}

func GetErrorNotifier() *ErrorNotifier {
	return globalErrorNotifier
}

func (n *ErrorNotifier) NotifyClient(connID string, err *models.WsError) error {
	conn, ok := n.connManager.GetConn(connID)
	if !ok {
		log.WarnK(connID, "ErrorNotifier,Process,GetConn,err=%s", err)
		return errors.New("connection not found: " + connID)
	}

	msg := err.ToErrorMessage()
	data, errEncode := models.EncodeErrorMessage(msg)
	if errEncode != nil {
		log.WarnK(connID, "ErrorNotifier,EncodeErrorMessage fail,err:%v", err)
		return errEncode
	}

	if errWrite := conn.SetWriteDeadline(time.Now().Add(2 * time.Second)); errWrite != nil {
		log.WarnK(connID, "ErrorNotifier,SetWriteDeadline,err:%v", err)
		return errWrite
	}

	if errWrite := conn.WriteMessage(websocket.TextMessage, data); errWrite != nil {
		log.WarnK(connID, "ErrorNotifier,SendMsg Fail,err:%v,RemoveConn", err)
		n.connManager.RemoveConn(connID)
		return errWrite
	}

	log.InfoK(connID, "ErrorNotifier,SendSuc,errCode=%s,message=%s", err.ErrorCode, err.Message)
	return nil
}

func (n *ErrorNotifier) NotifyClientAndClose(connID string, err *models.WsError) {
	_ = n.NotifyClient(connID, err)

	n.connManager.RemoveConn(connID)
	log.InfoK(connID, "ErrorNotifier,NotifyClientAndClose,err:%s,RemoveConn", err.Message)
}
