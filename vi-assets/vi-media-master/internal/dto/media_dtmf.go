package dto

import (
	"errors"
	"strings"
)

type MediaDtmf struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Audio play dataSource
	// Multiple values separated by commas
	// Can specify TTS as the dial tone
	AudioSource string `json:"audioSource"`
	// Audio loop play dataSource
	// Multiple values separated by commas
	// Can specify DuDu or Mute as the dial tone
	AudioLoopSource string `json:"audioLoopSource"`
	// Audio play default dataSource, only when the AudioSource not found
	// Multiple values separated by commas
	// Can specify TTS as the dial tone
	AudioDefaultSource string `json:"audioDefaultSource"`
	// The key representing the end sign
	EndSign string `json:"endSign"`
	// The key representing the back sign
	BackSign string `json:"backSign"`
	// The expected len for the dtmf
	ExpectLen int `json:"expectLen"`
	// The first digit reception timeout, with the unit seconds
	Timeout int `json:"timeout"`
	// The inter-digit timeout, with the unit seconds
	IntervalTimeout int `json:"intervalTimeout"`
	// Timing method:
	// 	1、 after the playback ends
	//	2、 throughout the entire duration
	TimingMode int `json:"timingMode" binding:"required,oneof=1 2"`
	// Interruptible or not: 1、 allowed；2、not allowed
	Interrupted int `json:"interrupted" binding:"required,oneof=1 2"`
	// Playback direction:
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	//	4. Diff caller and callee
	Direction int `json:"direction" binding:"required,oneof=1 2 3"`
	// Recording mode:
	// 	0、 no recording
	// 	1、 recording audio
	RecordMode int `json:"recordMode"`
	// Dtmf mode:
	// 1、single
	// 2、continuous
	Mode int `json:"mode" binding:"required,oneof=1 2"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}

func (m *MediaDtmf) Validate() error {
	m.AudioSource = strings.TrimSpace(m.AudioSource)
	m.AudioLoopSource = strings.TrimSpace(m.AudioLoopSource)
	m.AudioDefaultSource = strings.TrimSpace(m.AudioDefaultSource)
	m.EndSign = strings.TrimSpace(m.EndSign)
	m.BackSign = strings.TrimSpace(m.BackSign)
	if m.ExpectLen <= 0 && len(m.EndSign) == 0 {
		return errors.New("invalid config, expect ExpectLen or EndSign")
	}
	return nil
}