package dto

import (
	"errors"
	"strings"
)

type SessionStop struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Signaling end time
	EndTime string `json:"endTime" binding:"required"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}

func (s *SessionStop) Validate() error {
	s.EndTime = strings.TrimSpace(s.EndTime)
	if len(s.EndTime) == 0 {
		return errors.New("invalid endTime")
	}
	return nil
}