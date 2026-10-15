package dto

import (
	"errors"
	"strings"
)

type MediaPlay struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Audio play dataSource
	// Multiple values separated by commas
	// If they are different audio files, use a semicolon to separate them
	// Can specify DuDu or Mute or TTS as the dial tone
	AudioSource string `json:"audioSource"`
	// Audio loop play dataSource
	// Multiple values separated by commas
	// If they are different audio files, use a semicolon to separate them
	// Can specify DuDu or Mute or TTS as the dial tone
	AudioLoopSource string `json:"audioLoopSource"`
	// RTP audio codec
	// Only valid when the audio playback direction is diff, use a semicolon to separate them, especially has different encoding on both sides
	AudioCode string `json:"audioCode"`
	// RTP audio payloadType
	// Only valid when the audio playback direction is diff, use a semicolon to separate them, especially has different encoding on both sides
	AudioPayload string `json:"audioPayload"`
	// Video play dataSource
	// Multiple values separated by commas
	// If they are different video files, use a semicolon to separate them
	VideoSource string `json:"videoSource"`
	// Video loop play dataSource
	// Multiple values separated by commas
	// If they are different video files, use a semicolon to separate them
	VideoLoopSource string `json:"videoLoopSource"`
	// Initiated time period: Early、InCall
	Period string `json:"period" binding:"required,oneof=Early InCall"`
	// Playback priority: Local、Net
	Priority string `json:"priority" binding:"required,oneof=Local Net"`
	// Playback direction:
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	//	4. Diff caller and callee
	Direction int `json:"direction" binding:"required,oneof=1 2 3 4"`
	// Recording mode:
	// 	0、 no recording
	// 	1、 recording audio
	// 	2、 recording video
	// 	3、 recording audio and video
	RecordMode int `json:"recordMode"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}

func (m *MediaPlay) Validate() error {
	m.AudioSource = strings.TrimSpace(m.AudioSource)
	m.AudioLoopSource = strings.TrimSpace(m.AudioLoopSource)
	m.AudioCode = strings.TrimSpace(m.AudioCode)
	m.AudioPayload = strings.TrimSpace(m.AudioPayload)
	if m.Direction == 4 {
		if len(m.AudioCode) == 0 || len(m.AudioPayload) == 0 {
			return errors.New("invalid audioCode or audioPayload")
		}
		aCodes := strings.Split(m.AudioCode, ";")
		if len(aCodes) != 2 {
			return errors.New("invalid audioCode")
		}
		aCodes[0] = "audio/" + strings.ToLower(aCodes[0])
		aCodes[1] = "audio/" + strings.ToLower(aCodes[1])
		m.AudioCode = strings.Join(aCodes, ";")
	}
	m.VideoSource = strings.TrimSpace(m.VideoSource)
	m.VideoLoopSource = strings.TrimSpace(m.VideoLoopSource)
	if len(m.AudioSource) == 0 && len(m.AudioLoopSource) == 0 && len(m.VideoSource) == 0 && len(m.VideoLoopSource) == 0 {
		return errors.New("invalid mediaSource")
	}
	return nil
}