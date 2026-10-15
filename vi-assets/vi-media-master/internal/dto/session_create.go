package dto

import (
	"errors"
	"strings"
)

type SessionCreate struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Access ID
	AppId string `json:"userId" binding:"required"`
	// Caller number
	CallerNum string `json:"callerNum" binding:"required"`
	// Callee number
	CalleeNum string `json:"calleeNum"`
	// The merge configuration for the recording
	MergeConfig string `json:"mergeConfig" binding:"required"`
	// Start time, the time when the initial signaling is received
	StartTime string `json:"startTime" binding:"required"`
	// Used for notice, when the media playback ends, the dtmf ends, the livestream trans ends,
	// and the session timeout
	CallbackUrl string `json:"callbackUrl" binding:"required"`
	// Recording mode:
	// 	1、 recording
	// 	2、 full recording
	// 	3、 no recording
	RecordMode int `json:"recordMode" binding:"required,oneof=1 2 3"`
	// Inspect direction:
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	Direction int `json:"direction" binding:"required,oneof=1 2 3"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}

type SessionCreateResp struct {
	// Session unique ID
	SessionId string `json:"sessionId"`
	// RTP Listener IP
	IP string `json:"ip"`
	// RTP Listener audio port
	AudioPort int `json:"audioPort"`
	// RTP Listener video port
	VideoPort int `json:"videoPort"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId"`
}

func (s *SessionCreate) Validate() error {
	s.SessionId = strings.TrimSpace(s.SessionId)
	if len(s.SessionId) == 0 {
		return errors.New("invalid sessionId")
	}
	s.AppId = strings.TrimSpace(s.AppId)
	if len(s.AppId) == 0 {
		return errors.New("invalid appId")
	}
	s.CallerNum = strings.TrimSpace(s.CallerNum)
	if len(s.CallerNum) == 0 {
		return errors.New("invalid callerNum")
	}
	s.CalleeNum = strings.TrimSpace(s.CalleeNum)
	s.MergeConfig = strings.TrimSpace(s.MergeConfig)
	if len(s.MergeConfig) == 0 {
		return errors.New("invalid mergeConfig")
	}
	s.StartTime = strings.TrimSpace(s.StartTime)
	if len(s.StartTime) == 0 {
		return errors.New("invalid startTime")
	}
	s.CallbackUrl = strings.TrimSpace(s.CallbackUrl)
	if len(s.CallbackUrl) == 0 {
		return errors.New("invalid callbackUrl")
	}
	return nil
}