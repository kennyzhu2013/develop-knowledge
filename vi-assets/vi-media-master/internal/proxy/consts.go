package proxy

const (
	// MaxBufferSize MTU, unit byte
	MaxBufferSize = 1600
	// UdpReadDeadline UDP read timeout，unit milliseconds
	UdpReadDeadline = 10
	// MaxRtpLatency Max rtp latency，unit milliseconds
	MaxRtpLatency = 50000
)

const (
	RecordModeNeed = 1
	RecordModeAll  = 2
	RecordModeNot  = 3

	RecordNone  = 0
	RecordAudio = 1
	RecordVideo = 2
	RecordBoth  = 3

	RecordingIdentifierEarly        = "Early"
	RecordingIdentifierOriginCaller = "Caller"
	RecordingIdentifierOriginCallee = "Callee"
)

const (
	OutboundStreamHeaderSessionId = "X-Session-Id"
	OutboundStreamHeaderParams    = "X-Params"
	OutboundStreamHeaderNotice    = "X-Notice"
)

const MaxWaitEvents = 5
const (
	EventCreate       = "CREATE"
	EventUpdate       = "UPDATE"
	EventDtmf         = "DTMF"
	EventPlay         = "PLAY"
	EventStream       = "STREAM"
	EventStreamNotice = "STREAM_NOTICE"
	EventStop         = "STOP"
	EventPickup       = "PICKUP"
	EventCancel       = "CANCEL"
	EventFallback     = "FALLBACK"

	EventDtmfDone           = "DTMF_DONE"
	EventDtmfNotice         = "DTMF_NOTICE"
	EventAudioDone          = "AUDIO_DONE"
	EventVideoDone          = "VIDEO_DONE"
	EventStreamDone         = "STREAM_DONE"
	EventStreamOutOnSuccess = "STREAM_OUT_ONSUCCESS"
	EventStreamOutOnCancel  = "STREAM_OUT_ONCANCEL"
	EventSessionRelease     = "SESSION_RELEASE"

	StateNone    = "NONE"
	StateStart   = "START"
	StateActive  = "ACTIVE"
	StateInCall  = "IN_CALL"
	StateStopped = "STOPPED"
)