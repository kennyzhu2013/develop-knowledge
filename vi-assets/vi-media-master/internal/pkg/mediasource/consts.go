package mediasource

import "github.com/gorilla/websocket"

const ProtocolWS = "ws://"

const (
	KindAudio = 0x01
	KindVideo = 0x02
)

const (
	SourceCaller = 1
	SourceCallee = 2
	SourceEntire = 3
	SourceDiff   = 4
)

const (
	MessageTypeText   = websocket.TextMessage
	MessageTypeBinary = websocket.BinaryMessage
)

type Event string

const (
	EventData        Event = "DATA"
	EventFrame       Event = "FRAME"
	EventCalling     Event = "CALLING"
	EventCodecSwitch Event = "CODEC_SWITCH"
	EventEnd         Event = "END"
)

const (
	StateNew = iota
	StatePrepare
	StateRunning
	StateTerminated
)

const (
	NoticeStateBroken      = "BROKEN"
	NoticeStateException   = "EXCEPTION"
	NoticeStateTermination = "TERMINATION"
)