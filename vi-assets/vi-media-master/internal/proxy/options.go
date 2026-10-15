package proxy

import (
	"net"
	"sync"
)

type AudioOptions struct {
	// Audio source
	Source string
	// Audio loop source
	LoopSource string
	// Audio codec
	// Only valid when the audio playback direction is diff,
	// use a semicolon to separate them, especially has different encoding on both sides
	MimeType string
	// Audio payloadType
	// Only valid when the audio playback direction is diff,
	// use a semicolon to separate them, especially has different encoding on both sides
	Payload string
	// The core-network priority:
	//	Local
	//	Net
	Priority string
	// Playback direction:
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	Direction int
	// Bind an ID to differentiate between different playback events
	BindId uint8
	// The unique identifier for audio or video playback
	BindKey interface{}
	// Callback at the end
	Callback func()
	// Processing after sending an RTP packet
	Process func(int, []byte) error
	// Processing after the Source play done
	FinalizeProcess func()
}

type VideoOptions struct {
	// Video source
	Source string
	// Video loop source
	LoopSource string
	// Playback direction:
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	Direction int
	// Bind an ID to differentiate between different playback events
	BindId uint8
	// Callback at the end
	Callback func()
	// Processing after sending an RTP packet
	Process func(int, []byte) error
}

type RebindOptions struct {
	// The address of the audio/video
	AudioCallerAddr *net.UDPAddr
	AudioCalleeAddr *net.UDPAddr
	VideoCallerAddr *net.UDPAddr
	VideoCalleeAddr *net.UDPAddr
	// The payloadType of the audio/video
	AudioPayload uint8
	VideoPayload uint8
	// The mimeType of the audio/video
	AudioMimeType string
	VideoMimeType string
}

type SendOptions struct {
	// The audio or video data for looping playback
	// For audio, the data type is [][]byte, and for video, the data type is []*codec.Fragment
	LoopData interface{}
	// The handle when the playback ends
	Callback func()
	// Processing after sending an RTP packet
	Process func(int, []byte) error
	// Processing when a round ends
	FinalizeProcess func()
	// Wait handle
	WaitHandle *sync.WaitGroup
}

type SendOption func(*SendOptions)

func LoopData(loopData interface{}) SendOption {
	return func(opts *SendOptions) {
		opts.LoopData = loopData
	}
}

func Finalize(process func()) SendOption {
	return func(opts *SendOptions) {
		opts.FinalizeProcess = process
	}
}

func Callback(callback func()) SendOption {
	return func(opts *SendOptions) {
		opts.Callback = callback
	}
}

func Process(process func(int, []byte) error) SendOption {
	return func(opts *SendOptions) {
		opts.Process = process
	}
}