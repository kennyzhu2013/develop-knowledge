package codec

import (
	"errors"
	"strings"
	"sync"
)

type MediaKind int

const (
	MediaKindAudio MediaKind = iota + 1
	MediaKindVideo
)

func (t MediaKind) String() string {
	switch t {
	case MediaKindAudio:
		return "audio"
	case MediaKindVideo:
		return "video"
	default:
		return "unknown"
	}
}

const (
	MimeTypeAmrNB      = "audio/amr"
	MimeTypeAmrNBOctet = "audio/amr-octet"
	MimeTypeAmrWB      = "audio/amr-wb"
	MimeTypeAmrWBOctet = "audio/amr-wb-octet"
	MimeTypePCMA       = "audio/pcma"
	MimeTypePCMU       = "audio/pcmu"
	MimeTypeLowH264    = "video/h264-25"
	MimeTypeMidH264    = "video/h264-30"
	MimeTypeHigH264    = "video/h264-50"
	MimeTypeExtH264    = "video/h264-60"
)

var (
	ErrUnknownType     = errors.New("unknown")
	ErrConflictingType = errors.New("conflicting")
)

type AVParameters struct {
	MimeType  string
	ClockRate uint32
	FrameRate uint32
}

type AVEngine struct {
	mu      sync.RWMutex
	aCodecs map[string]AVParameters
	vCodecs map[string]AVParameters
}

func (e *AVEngine) Register(p AVParameters) error {
	e.mu.Lock()
	defer e.mu.Unlock()
	if strings.HasPrefix(p.MimeType, "audio/") {
		if _, ok := e.aCodecs[p.MimeType]; ok {
			return ErrConflictingType
		}
		e.aCodecs[p.MimeType] = p
		return nil
	}
	if strings.HasPrefix(p.MimeType, "video/") {
		if _, ok := e.vCodecs[p.MimeType]; ok {
			return ErrConflictingType
		}
		e.vCodecs[p.MimeType] = p
		return nil
	}
	return ErrUnknownType
}

func (e *AVEngine) Find(mimeType string) (AVParameters, bool) {
	e.mu.RLock()
	defer e.mu.RUnlock()
	if strings.HasPrefix(mimeType, "audio/") {
		p, ok := e.aCodecs[mimeType]
		return p, ok
	}
	if strings.HasPrefix(mimeType, "video/") {
		p, ok := e.vCodecs[mimeType]
		return p, ok
	}
	return AVParameters{}, false
}

var engine *AVEngine

func init() {
	engine = &AVEngine{aCodecs: make(map[string]AVParameters, 8), vCodecs: make(map[string]AVParameters, 4)}
	engine.aCodecs[MimeTypeAmrNB] = AVParameters{MimeType: MimeTypeAmrNB, ClockRate: 8000, FrameRate: 50}
	engine.aCodecs[MimeTypeAmrNBOctet] = AVParameters{MimeType: MimeTypeAmrNBOctet, ClockRate: 8000, FrameRate: 50}
	engine.aCodecs[MimeTypeAmrWB] = AVParameters{MimeType: MimeTypeAmrWB, ClockRate: 16000, FrameRate: 50}
	engine.aCodecs[MimeTypeAmrWBOctet] = AVParameters{MimeType: MimeTypeAmrWBOctet, ClockRate: 16000, FrameRate: 50}
	engine.aCodecs[MimeTypePCMA] = AVParameters{MimeType: MimeTypePCMA, ClockRate: 8000, FrameRate: 50}
	engine.aCodecs[MimeTypePCMU] = AVParameters{MimeType: MimeTypePCMU, ClockRate: 8000, FrameRate: 50}
	engine.vCodecs[MimeTypeLowH264] = AVParameters{MimeType: MimeTypeLowH264, ClockRate: 90000, FrameRate: 25}
	engine.vCodecs[MimeTypeMidH264] = AVParameters{MimeType: MimeTypeMidH264, ClockRate: 90000, FrameRate: 30}
	engine.vCodecs[MimeTypeHigH264] = AVParameters{MimeType: MimeTypeHigH264, ClockRate: 90000, FrameRate: 50}
	engine.vCodecs[MimeTypeExtH264] = AVParameters{MimeType: MimeTypeExtH264, ClockRate: 90000, FrameRate: 60}
}

func Register(p AVParameters) error {
	return engine.Register(p)
}

func Find(mimeType string) (AVParameters, bool) {
	return engine.Find(mimeType)
}

func KindForMimeType(mimeType string) MediaKind {
	switch {
	case strings.HasPrefix(mimeType, "audio/"):
		return MediaKindAudio
	case strings.HasPrefix(mimeType, "video/"):
		return MediaKindVideo
	default:
		return MediaKind(0)
	}
}

func VideoMimeTypeForFPS(fps uint32) string {
	switch fps {
	case 25:
		return MimeTypeLowH264
	case 30:
		return MimeTypeMidH264
	case 50:
		return MimeTypeHigH264
	case 60:
		return MimeTypeExtH264
	default:
		return MimeTypeMidH264
	}
}