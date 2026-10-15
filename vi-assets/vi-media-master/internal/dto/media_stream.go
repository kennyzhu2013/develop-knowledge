package dto

import "strings"

type MediaStream struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Audio play dataSource
	// Multiple values separated by commas
	AudioSource string `json:"audioSource"`
	// Audio loop play dataSource
	// Multiple values separated by commas
	// Can specify Mute as the dial tone
	AudioLoopSource string `json:"audioLoopSource"`
	// Video play dataSource, Format:
	// 	File:f1,f2;f3,f4
	// 	Live:Normal[:URL]
	//	Live:Audio[:URL]
	//	Live:Video[:URL]
	MediaSource string `json:"mediaSource"`
	// Video loop play dataSource
	// Multiple values separated by commas
	VideoLoopSource string `json:"videoLoopSource"`
	// The dataSource config params
	Config string `json:"config" binding:"required"`
	// Whether to push the video stream
	// 	0、not push
	// 	1、push
	PushVideo int `json:"pushVideo"`
	// Recording mode:
	// 	0、 no recording
	// 	1、 recording audio
	// 	2、 recording video
	// 	3、 recording audio and video
	RecordMode int `json:"recordMode"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}

func (m *MediaStream) Validate() error {
	m.AudioSource = strings.TrimSpace(m.AudioSource)
	m.AudioLoopSource = strings.TrimSpace(m.AudioLoopSource)
	m.MediaSource = strings.TrimSpace(m.MediaSource)
	m.VideoLoopSource = strings.TrimSpace(m.VideoLoopSource)
	m.Config = strings.TrimSpace(m.Config)
	return nil
}