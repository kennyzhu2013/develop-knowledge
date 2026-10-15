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
	// The frame count threshold for enabling inspection
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