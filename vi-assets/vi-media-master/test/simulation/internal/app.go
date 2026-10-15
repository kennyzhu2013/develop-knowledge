package internal

import (
	"errors"
	"fmt"
	_ "net/http/pprof"
	"os"
	"simulation/pkg/engine"
	"simulation/pkg/rtp/codec"
	"strings"
)

const (
	KeySessionCreate = "CREATE"
	KeySessionUpdate = "UPDATE"
	KeyMediaInspect  = "INSPECT"
	KeyMediaNotice   = "NOTICE"
	KeySessionPickup = "PICKUP"
	KeySessionStop   = "STOP"

	KeySequence   = "SEQUENCE"
	KeyAudioPort  = "AUDIO_PORT"
	KeyVideoPort  = "VIDEO_PORT"
	KeyPickupFLag = "PICKUP_FLAG"
	KeyMimeType   = "MIME_TYPE"
	KeyStartTime  = "START_TIME"
	KeyVersion    = "VERSION"
	KeyEnd        = "END"
)

var (
	config Config
	urls   map[string]string

	audioPayloads = map[string]uint8{"audio/amr": 106, "audio/amr-wb": 106, "audio/pcma": 8, "audio/pcmu": 0}
)

type App struct {
	engine *engine.Engine
}

func New(cfg Config) (*App, error) {
	// Logger
	InitLog("./", "all.log", "info")
	// Load data
	lAudioSources := strings.Split(cfg.CallerAudioSource, ",")
	rAudioSources := strings.Split(cfg.CalleeAudioSource, ",")
	if len(lAudioSources) != len(rAudioSources) || len(lAudioSources) == 0 || len(rAudioSources) == 0 {
		return nil, errors.New("invalid audio source")
	}
	for i := range lAudioSources {
		lMime := strings.Split(lAudioSources[i], "@")
		rMime := strings.Split(rAudioSources[i], "@")
		if len(lMime) != 2 || len(rMime) != 2 || lMime[0] != rMime[0] {
			return nil, errors.New("invalid audio source")
		}
		lData, err := os.ReadFile(lMime[1])
		if err != nil {
			return nil, err
		}
		rData, err := os.ReadFile(rMime[1])
		if err != nil {
			return nil, err
		}
		cfg.audioMimeType = append(cfg.audioMimeType, "audio/"+strings.ToLower(lMime[0]))
		cfg.callerAudioData = append(cfg.callerAudioData, codec.ShardingFile(cfg.audioMimeType[i], lData)[0].Frames)
		cfg.calleeAudioData = append(cfg.calleeAudioData, codec.ShardingFile(cfg.audioMimeType[i], rData)[0].Frames)
	}
	// Init engine
	eni, err := engine.New(engine.Configuration{MaxG: cfg.MaxG, MinP: cfg.ImsPort})
	if err != nil {
		return nil, err
	}
	eni.Add(SessionCreate).Add(SessionUpdate).Add(MediaInspect).Add(SessionPickup).Add(SessionStop)
	config = cfg
	return &App{engine: eni}, nil
}

func (a *App) Start() error {
	urls = make(map[string]string, 8)
	urls[KeySessionCreate] = fmt.Sprintf("http://%s/call/session/create", config.ServerAddr)
	urls[KeySessionUpdate] = fmt.Sprintf("http://%s/call/session/update", config.ServerAddr)
	urls[KeyMediaInspect] = fmt.Sprintf("http://%s/call/media/inspect", config.ServerAddr)
	urls[KeyMediaNotice] = fmt.Sprintf("http://%s/media/notice", config.LocalAddr)
	urls[KeySessionPickup] = fmt.Sprintf("http://%s/call/session/pickup", config.ServerAddr)
	urls[KeySessionStop] = fmt.Sprintf("http://%s/call/session/stop", config.ServerAddr)
	if config.Loop {
		a.engine.DoLoop()
	} else {
		a.engine.Do()
	}
	a.engine.Wait()
	return nil
}