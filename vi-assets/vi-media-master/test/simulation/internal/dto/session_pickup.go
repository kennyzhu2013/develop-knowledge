package dto

import (
	"errors"
	"strings"
)

type SessionPickup struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Signaling connection time
	StartTime string `json:"startTime" binding:"required"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}

func (s *SessionPickup) Validate() error {
	s.StartTime = strings.TrimSpace(s.StartTime)
	if len(s.StartTime) == 0 {
		return errors.New("invalid startTime")
	}
	return nil
}