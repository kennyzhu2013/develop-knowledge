package dto

import (
	"errors"
	"strings"
)

type MediaInspect struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// The config params
	Config string `json:"config" binding:"required"`
	// The frame count threshold for enabling inspection, unit seconds
	Frames int `json:"frames" binding:"required,gt=0"`
	// Inspect direction:
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	Direction int `json:"direction" binding:"required,oneof=1 2 3"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}

func (m *MediaInspect) Validate() error {
	m.SessionId = strings.TrimSpace(m.SessionId)
	if len(m.SessionId) == 0 {
		return errors.New("invalid sessionId")
	}
	m.Config = strings.TrimSpace(m.Config)
	if len(m.Config) == 0 {
		return errors.New("invalid config")
	}
	return nil
}

type MediaInspectNotice struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// The detail of the event
	Message string `json:"message" binding:"required"`
	// The event name: EXCEPTION
	Event string `json:"event" binding:"required,oneof=EXCEPTION TERMINATION"`
	// The event data
	Data interface{} `json:"data"`
}

func (m *MediaInspectNotice) Validate() error {
	m.SessionId = strings.TrimSpace(m.SessionId)
	if len(m.SessionId) == 0 {
		return errors.New("invalid sessionId")
	}
	m.Message = strings.TrimSpace(m.Message)
	if len(m.Message) == 0 {
		return errors.New("invalid message")
	}
	return nil
}