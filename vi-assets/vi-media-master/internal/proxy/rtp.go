package proxy

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"github.com/gorilla/websocket"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/avcache"
	"vi-media/internal/pkg/kv"
	"vi-media/internal/pkg/kv/registry"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/mediasource"
	"vi-media/internal/pkg/tts"
	"vi-media/pkg/format/container"
	"vi-media/pkg/rtp"
	"vi-media/pkg/rtp/codec"
)

type audioKey struct{}
type videoKey struct{}

const KeyAudioCapture = "/vi-media/audio-capture"

var (
	duduMap  = make(map[string][][]byte, 8)
	duduFile = map[string]struct{}{"global/global_dudu_nb.amr": {}, "global/global_dudu_wb.amr": {}, "global/global_dudu_pcma.wav": {},
		"global/global_dudu_pcmu.wav": {}, "global/global_dudu_bcg.g729": {}}
)

var (
	aKey audioKey
	vKey videoKey

	aCache map[interface{}]*avcache.Manager[*avcache.AudioItem]
	vCache *avcache.Manager[*avcache.VideoItem]

	aSharding = func(mimeType, name string, data []byte) (item *avcache.AudioItem, err error) {
		defer func() {
			if v := recover(); v != nil {
				err = fmt.Errorf("recovered from aSharding with %v, stack: %v", v, process.Stack())
				item = nil
			}
		}()
		fg := codec.ShardingFile(mimeType, data)
		if len(fg) == 0 {
			return nil, fmt.Errorf("%v not suppoted", mimeType)
		}
		item = avcache.NewAudioItem(name, mimeType, fg[0])
		if mimeType == codec.MimeTypeAmrNB {
			fg = codec.ShardingFile(codec.MimeTypeAmrNBOctet, data)
			item.SetOct(fg[0])
		} else if mimeType == codec.MimeTypeAmrWB {
			fg = codec.ShardingFile(codec.MimeTypeAmrWBOctet, data)
			item.SetOct(fg[0])
		}
		return
	}

	vSharding = func(mimeType, name string, data []byte) (item *avcache.VideoItem, err error) {
		defer func() {
			if v := recover(); v != nil {
				err = fmt.Errorf("recovered from vSharding with %v, stack: %v", v, process.Stack())
				item = nil
			}
		}()
		// Only supported 3gp container
		if strings.HasSuffix(strings.ToLower(name), ".3gp") {
			gpData, err := container.Demux3GP(data)
			if err != nil {
				return nil, err
			}
			data = gpData.Video
			mimeType = codec.VideoMimeTypeForFPS(gpData.Fps)
		}
		fg := codec.ShardingFile(mimeType, data)
		if len(fg) == 0 {
			return nil, errors.New("invalid video")
		}
		return avcache.NewVideoItem(name, mimeType, fg), nil
	}
)

var aLB, vLB registry.LoadBalancer

func initAvCache(ctx context.Context) error {
	if registry.LoadBalancerType(conf.App.Cache.Cdn.AudioLoadBalancer) == registry.LoadBalancerTypeRoundRobin {
		aLB = registry.NewRoundRobinLoadBalancer()
	} else {
		aLB = registry.NewConsistentHashLoadBalancer()
	}
	if registry.LoadBalancerType(conf.App.Cache.Cdn.VideoLoadBalancer) == registry.LoadBalancerTypeHashRing {
		vLB = registry.NewConsistentHashLoadBalancer()
	} else {
		vLB = registry.NewRoundRobinLoadBalancer()
	}
	// Load audio from cdn
	cache := avcache.NewSimpleCache[*avcache.AudioItem](conf.App.Cache.MaxAudioCapacity * 1024 * 1024)
	if conf.App.Cache.Cdn.Startup {
		host, err := registry.GetNode(conf.App.Cache.Cdn.ClusterName, conf.App.Cache.Cdn.AppName, "", aLB)
		if err != nil {
			return err
		}
		conn, _, err := (&websocket.Dialer{Proxy: http.ProxyFromEnvironment, HandshakeTimeout: 5 * time.Second}).DialContext(context.Background(),
			fmt.Sprintf("ws://%v%v", host, conf.App.Cache.Cdn.Uri), nil)
		if err != nil {
			return err
		}
		defer func() { _ = conn.Close() }()
		for {
			_, data, rErr := conn.ReadMessage()
			if rErr != nil {
				return rErr
			}
			if len(data) == 3 && string(data) == "END" {
				_ = conn.WriteControl(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""), time.Now().Add(time.Second))
				_ = conn.Close()
				break
			}
			if len(data) <= 8 {
				continue
			}
			nLength := binary.BigEndian.Uint32(data)
			if nLength == 0 {
				continue
			}
			mLength := binary.BigEndian.Uint32(data[4:])
			if mLength == 0 {
				continue
			}
			if len(data) < int(8+nLength+mLength) {
				continue
			}
			name := string(data[8 : 8+nLength])
			mimeType := string(data[8+nLength : 8+nLength+mLength])
			item, sErr := aSharding(mimeType, name, data[8+nLength+mLength:])
			if sErr != nil {
				continue
			}
			if _, ok := duduFile[name]; ok {
				duduMap[mimeType] = item.Get(mimeType).Frames
				if strings.HasSuffix(name, "_nb.amr") {
					duduMap[codec.MimeTypeAmrNBOctet] = item.Get(codec.MimeTypeAmrNBOctet).Frames
				} else if strings.HasSuffix(name, "_wb.amr") {
					duduMap[codec.MimeTypeAmrWBOctet] = item.Get(codec.MimeTypeAmrWBOctet).Frames
				}
			}
			cache.Put(item)
		}
	}
	initCircuitBreaker()
	aCache = make(map[interface{}]*avcache.Manager[*avcache.AudioItem], 2)
	aCache[aKey] = avcache.New[*avcache.AudioItem](conf.App.Cache.BaseAudioPath, cache, func(ctx context.Context, mimeType string, name string) (*avcache.AudioItem, error) {
		addr, gErr := registry.GetNode(conf.App.Cache.Cdn.ClusterName, conf.App.Cache.Cdn.AppName, name, aLB)
		if gErr != nil {
			return nil, gErr
		}
		status, data, rErr := postWithContext(ctx, fmt.Sprintf("http://%v%v?name=%v", addr, conf.App.Cache.Cdn.AudioData, url.PathEscape(name)), nil)
		if rErr != nil {
			return nil, rErr
		}
		if status == http.StatusNotFound {
			return nil, avcache.ErrNotExists
		}
		if status != http.StatusOK {
			return nil, fmt.Errorf("get audio source failed with %v", string(data))
		}
		return aSharding(mimeType, name, data)
	})
	aCache[vKey] = avcache.New[*avcache.AudioItem](conf.App.Cache.BaseVideoPath, avcache.NewWTinyLfuCache[*avcache.AudioItem](conf.App.Cache.MaxVideoCapacity),
		func(ctx context.Context, mimeType string, name string) (*avcache.AudioItem, error) {
			addr, gErr := registry.GetNode(conf.App.Cache.Cdn.ClusterName, conf.App.Cache.Cdn.AppName, name, vLB)
			if gErr != nil {
				return nil, gErr
			}
			status, data, rErr := cbPost(ctx, fmt.Sprintf("http://%v%v?name=%v", addr, conf.App.Cache.Cdn.VideoData, url.PathEscape(name)), nil, name)
			if rErr != nil {
				return nil, rErr
			}
			if status == http.StatusNotFound {
				return nil, avcache.ErrNotExists
			}
			if status != http.StatusOK {
				return nil, fmt.Errorf("get audio source for video failed with %v", string(data))
			}
			return aSharding(mimeType, name, data)
		})
	vCache = avcache.New[*avcache.VideoItem](conf.App.Cache.BaseVideoPath, avcache.NewWTinyLfuCache[*avcache.VideoItem](conf.App.Cache.MaxVideoCapacity),
		func(ctx context.Context, mimeType string, name string) (*avcache.VideoItem, error) {
			addr, gErr := registry.GetNode(conf.App.Cache.Cdn.ClusterName, conf.App.Cache.Cdn.AppName, name, vLB)
			if gErr != nil {
				return nil, gErr
			}
			status, data, rErr := cbPost(ctx, fmt.Sprintf("http://%v%v?name=%v", addr, conf.App.Cache.Cdn.VideoData, url.PathEscape(name)), nil, name)
			if rErr != nil {
				return nil, rErr
			}
			if status == http.StatusNotFound {
				return nil, avcache.ErrNotExists
			}
			if status != http.StatusOK {
				return nil, fmt.Errorf("get video source failed with %v", string(data))
			}
			return vSharding(mimeType, name, data)
		})
	// Watch audio capture event
	kv.Watch(ctx, KeyAudioCapture, func(names []byte) error { return rSync(string(names)) })
	return nil
}

func getDuDu(_ *Session, mimeType string) ([][]byte, error) {
	data := duduMap[mimeType]
	if len(data) != 0 {
		return data, nil
	}
	name := ""
	ctx, cancelFunc := context.WithDeadline(context.Background(), time.Now().Add(time.Duration(conf.App.Cache.Cdn.AudioTimeout)*time.Second))
	defer cancelFunc()
	switch mimeType {
	case codec.MimeTypeAmrNB:
		name = "global/global_dudu_nb.amr"
	case codec.MimeTypeAmrNBOctet:
		name = "global/global_dudu_nb.amr"
	case codec.MimeTypeAmrWB:
		name = "global/global_dudu_wb.amr"
	case codec.MimeTypeAmrWBOctet:
		name = "global/global_dudu_wb.amr"
	case codec.MimeTypePCMA:
		name = "global/global_dudu_pcma.wav"
	case codec.MimeTypePCMU:
		name = "global/global_dudu_pcmu.wav"
	case codec.MimeTypeG729:
		name = "global/global_dudu_bcg.g729"
	}
	audio, _, err := aCache[aKey].Get(ctx, mimeType, name)
	if err != nil {
		return nil, err
	}
	return audio.Get(mimeType).Frames, nil
}

func getAudio(session *Session, key interface{}, mimeType, source string) ([][]byte, error) {
	if source == AudioMute {
		return [][]byte{muteCache[mimeType]}, nil
	}
	if source == AudioDudu {
		return getDuDu(session, mimeType)
	}
	if strings.HasPrefix(source, AudioTTS) {
		return tts.Get(session.id, ":"+conf.App.MediaSource.Outbound.AppName+conf.App.MediaSource.Outbound.TTS, strings.TrimPrefix(source, AudioTTS+":"), mimeType)
	}
	var errs []error
	var audioData [][]byte
	ctx, cancelFunc := context.WithDeadline(context.Background(), time.Now().Add(time.Duration(conf.App.Cache.Cdn.AudioTimeout)*time.Second))
	defer cancelFunc()
	for _, part := range strings.Split(source, DelimiterComma) {
		audio, _, err := aCache[key].Get(ctx, mimeType, part)
		if err != nil {
			errs = append(errs, err)
			continue
		}
		audioData = append(audioData, audio.Get(mimeType).Frames...)
	}
	if len(errs) != 0 {
		return nil, errors.Join(errs...)
	}
	if len(audioData) == 0 {
		return nil, errors.New("empty audio")
	}
	return audioData, nil
}

func getVideo(mimeType, source string) ([]*codec.Fragment, error) {
	var errs []error
	var videoData []*codec.Fragment
	ctx, cancelFunc := context.WithDeadline(context.Background(), time.Now().Add(time.Duration(conf.App.Cache.Cdn.VideoTimeout)*time.Second))
	defer cancelFunc()
	for _, part := range strings.Split(source, DelimiterComma) {
		video, _, err := vCache.Get(ctx, mimeType, part)
		if err != nil {
			errs = append(errs, err)
			continue
		}
		videoData = append(videoData, video.Get()...)
	}
	if len(errs) != 0 {
		return nil, errors.Join(errs...)
	}
	if len(videoData) == 0 {
		return nil, errors.New("empty video")
	}
	return videoData, nil
}

func rSync(names string) error {
	var errs []error
	var err error
	ctx := context.Background()
	for _, name := range strings.Split(names, DelimiterComma) {
		if strings.HasSuffix(name, "_nb.amr") {
			_, _, err = aCache[aKey].Load(ctx, codec.MimeTypeAmrNB, name)
		} else if strings.HasSuffix(name, "_wb.amr") {
			_, _, err = aCache[aKey].Load(ctx, codec.MimeTypeAmrWB, name)
		} else if strings.HasSuffix(name, "_pcma.wav") {
			_, _, err = aCache[aKey].Load(ctx, codec.MimeTypePCMA, name)
		} else if strings.HasSuffix(name, "_pcmu.wav") {
			_, _, err = aCache[aKey].Load(ctx, codec.MimeTypePCMU, name)
		} else if strings.HasSuffix(name, "_bcg.g729") {
			_, _, err = aCache[aKey].Load(ctx, codec.MimeTypeG729, name)
		}
		if err != nil {
			errs = append(errs, err)
		}
	}
	if len(errs) != 0 {
		return errors.Join(errs...)
	}
	return nil
}

func Load(name string) error {
	var errs []error
	ctx := context.Background()
	_, _, err := aCache[aKey].Load(ctx, codec.MimeTypeAmrNB, name+"_nb.amr")
	if err != nil {
		errs = append(errs, err)
	}
	_, _, err = aCache[aKey].Load(ctx, codec.MimeTypeAmrWB, name+"_wb.amr")
	if err != nil {
		errs = append(errs, err)
	}
	_, _, err = aCache[aKey].Load(ctx, codec.MimeTypePCMA, name+"_pcma.wav")
	if err != nil {
		errs = append(errs, err)
	}
	_, _, err = aCache[aKey].Load(ctx, codec.MimeTypePCMU, name+"_pcmu.wav")
	if err != nil {
		errs = append(errs, err)
	}
	_, _, err = aCache[aKey].Load(ctx, codec.MimeTypeG729, name+"_bcg.g729")
	if err != nil {
		errs = append(errs, err)
	}
	if len(errs) != 0 {
		return errors.Join(errs...)
	}
	return nil
}

func Evict(names string) error {
	for _, name := range strings.Split(names, DelimiterComma) {
		aCache[aKey].Remove(name)
	}
	return nil
}

func Exists(name string) string {
	return aCache[aKey].Exists(name)
}

func Elements() int {
	return aCache[aKey].Elements()
}

const (
	StateIdle    = 0
	StatePlaying = 1

	PriorityNet   = "Net"
	PriorityLocal = "Local"

	DelimiterComma     = ","
	DelimiterSemicolon = ";"
	DelimiterCodec     = "_"
)

const (
	AudioTTS  = "TTS"
	AudioMute = "Mute"
	AudioDudu = "DuDu"

	TrackAudio       = "Track-Audio"
	TrackCallerAudio = "Track-Caller-Audio"
	TrackCalleeAudio = "Track-Callee-Audio"
	TrackVideo       = "Track-Video"
	TrackCallerVideo = "Track-Caller-Video"
	TrackCalleeVideo = "Track-Callee-Video"

	BindingCallerAudio = "Binding-Caller-Audio"
	BindingCalleeAudio = "Binding-Callee-Audio"
	BindingCallerVideo = "Binding-Caller-Video"
	BindingCalleeVideo = "Binding-Callee-Video"
)

type setupOptions struct {
	// The current audio and video playback state
	audioState uint8
	videoState uint8
	// The current audio source
	audioSource string
	// The current loop audio source
	audioLoopSource string
	// The core-network priority: PriorityNet PriorityLocal
	priority string
	// Indicates if the core-network voice stream is ready
	netReady bool
	// The current playback direction
	direction int
	// Bind an ID to differentiate between different playback events
	bindId uint8
	// The unique identifier for audio or video playback
	bindKey interface{}
}

func (o *setupOptions) setupAudio(opts *AudioOptions) {
	o.bindId = opts.BindId
	o.bindKey = opts.BindKey
	o.audioSource = opts.Source
	o.audioLoopSource = opts.LoopSource
	o.direction = opts.Direction
	o.priority = opts.Priority
	o.audioState = StatePlaying
}

type rtpPlayer struct {
	wg sync.WaitGroup
	// The current playback options
	setupOptions

	// The audio or video source from inbound/outbound stream
	inOutAudio chan [][]byte
	inOutVideo chan []*codec.Fragment

	audioSender *rtpSender
	videoSender *rtpSender
}

func (p *rtpPlayer) StartAudio(session *Session, opts AudioOptions) error {
	if p.audioState == StatePlaying {
		return errors.New("the audio has already played")
	}
	if opts.Direction == mediasource.SourceCaller && opts.Priority == PriorityNet && p.netReady {
		return errors.New("the core-network voice stream has arrived")
	}
	if opts.Direction == mediasource.SourceDiff {
		return p.startDiffAudio(session, &opts)
	}
	var err error
	var audio, loopAudio [][]byte
	if len(opts.Source) != 0 {
		audio, err = getAudio(session, aKey, session.audioMimeType, opts.Source)
		if err != nil {
			return err
		}
	}
	if len(opts.LoopSource) != 0 {
		loopAudio, err = getAudio(session, aKey, session.audioMimeType, opts.LoopSource)
		if err != nil {
			return err
		}
	}
	if len(audio) == 0 && len(loopAudio) == 0 {
		return errors.New("the audio source is empty")
	}
	if len(audio) == 0 {
		audio = loopAudio
	}
	audioSender := newRtpSender(session)
	if opts.Direction != mediasource.SourceCallee {
		err = audioSender.Bind(TrackAudio, session.audioMimeType, session.streamAudioProxy.conn, rtp.TrackContext{Id: BindingCallerAudio, Addr: session.streamAudioProxy.callerAddr, PT: session.audioPayload})
	}
	if err != nil {
		return err
	}
	if opts.Direction != mediasource.SourceCaller {
		err = audioSender.Bind(TrackAudio, session.audioMimeType, session.streamAudioProxy.conn, rtp.TrackContext{Id: BindingCalleeAudio, Addr: session.streamAudioProxy.calleeAddr, PT: session.audioPayload})
	}
	if err != nil {
		return err
	}
	p.audioSender = audioSender
	p.setupAudio(&opts)
	go p.audioSender.SendAudio(TrackAudio, audio, LoopData(loopAudio), Process(opts.Process), Callback(opts.Callback), Finalize(opts.FinalizeProcess))
	return nil
}

func (p *rtpPlayer) startDiffAudio(session *Session, opts *AudioOptions) error {
	var err error
	var data [][]byte
	var audio, loopAudio [2][][]byte
	payload := strings.Split(opts.Payload, DelimiterSemicolon)
	mimeType := strings.Split(opts.MimeType, DelimiterSemicolon)
	if len(payload) != 2 || len(mimeType) != 2 {
		return errors.New("invalid mimeType with payload, not diff")
	}
	if len(opts.Source) != 0 {
		for i, source := range strings.Split(opts.Source, DelimiterSemicolon) {
			data, err = getAudio(session, opts.BindKey, mimeType[i], source)
			if err != nil {
				return err
			}
			audio[i] = data
		}
	}
	if len(opts.LoopSource) != 0 {
		for i, source := range strings.Split(opts.LoopSource, DelimiterSemicolon) {
			data, err = getAudio(session, opts.BindKey, mimeType[i], source)
			if err != nil {
				return err
			}
			loopAudio[i] = data
		}
	}
	if (len(audio[0]) == 0 && len(loopAudio[0]) == 0) || (len(audio[1]) == 0 && len(loopAudio[1]) == 0) {
		return errors.New("the audio source is empty")
	}
	if len(audio[0]) == 0 {
		audio[0] = loopAudio[0]
	}
	if len(audio[1]) == 0 {
		audio[1] = loopAudio[1]
	}
	audioSender := newRtpSender(session)
	pp, _ := strconv.Atoi(payload[0])
	err = audioSender.Bind(TrackCallerAudio, mimeType[0], session.streamAudioProxy.conn, rtp.TrackContext{Id: BindingCallerAudio, Addr: session.streamAudioProxy.callerAddr, PT: uint8(pp)})
	if err != nil {
		return err
	}
	pp, _ = strconv.Atoi(payload[1])
	err = audioSender.Bind(TrackCalleeAudio, mimeType[1], session.streamAudioProxy.conn, rtp.TrackContext{Id: BindingCalleeAudio, Addr: session.streamAudioProxy.calleeAddr, PT: uint8(pp)})
	if err != nil {
		return err
	}
	p.audioSender = audioSender
	p.setupAudio(opts)
	go p.audioSender.SendVaryAudio([]trackData[[][]byte]{{TrackCallerAudio, audio[0], loopAudio[0]}, {TrackCalleeAudio, audio[1], loopAudio[1]}}, Process(opts.Process),
		Callback(opts.Callback), Finalize(opts.FinalizeProcess))
	return nil
}

func (p *rtpPlayer) StartVideo(session *Session, aOpts AudioOptions, vOpts VideoOptions) error {
	if p.videoState == StatePlaying {
		return errors.New("the video has already played")
	}
	if vOpts.Direction == mediasource.SourceDiff {
		return p.startDiffVideo(session, &aOpts, &vOpts)
	}
	// load video
	var err error
	var video, loopVideo []*codec.Fragment
	if len(vOpts.Source) != 0 {
		video, err = getVideo(session.videoMimeType, vOpts.Source)
		if err != nil {
			return err
		}
	}
	if len(vOpts.LoopSource) != 0 {
		loopVideo, err = getVideo(session.videoMimeType, vOpts.LoopSource)
		if err != nil {
			return err
		}
	}
	if len(video) == 0 && len(loopVideo) == 0 {
		return errors.New("the video source is empty")
	}
	if len(video) == 0 {
		video = loopVideo
	}
	videoSender := newRtpSender(session)
	if vOpts.Direction != mediasource.SourceCallee {
		err = videoSender.Bind(TrackVideo, session.videoMimeType, session.streamVideoProxy.conn, rtp.TrackContext{Id: BindingCallerVideo, Addr: session.streamVideoProxy.callerAddr, PT: session.videoPayload})
	}
	if err != nil {
		return err
	}
	if vOpts.Direction != mediasource.SourceCaller {
		err = videoSender.Bind(TrackVideo, session.videoMimeType, session.streamVideoProxy.conn, rtp.TrackContext{Id: BindingCalleeVideo, Addr: session.streamVideoProxy.calleeAddr, PT: session.videoPayload})
	}
	if err != nil {
		return err
	}
	// Load audio
	// Partial negotiation, if it fails, then directly return error
	if strings.Contains(aOpts.MimeType, DelimiterSemicolon) && (len(aOpts.Source) != 0 || len(aOpts.LoopSource) != 0) {
		err = p.startDiffAudio(session, &aOpts)
		if err != nil {
			return err
		}
		p.videoSender = videoSender
		go p.videoSender.SendVideo(session.id, video, LoopData(loopVideo), Process(vOpts.Process), Callback(vOpts.Callback))
		p.videoState = StatePlaying
		return nil
	}
	// Unilateral playback
	// If it fails, then directly fall back
	audioReady := true
	var audio, loopAudio [][]byte
	if len(aOpts.Source) != 0 {
		audio, err = getAudio(session, vKey, session.audioMimeType, aOpts.Source)
		if err != nil {
			log.WarnK(session.id, "Load audio failed, source: %v, cause: %v", aOpts.Source, err)
			audioReady = false
		}
	}
	if audioReady && len(aOpts.LoopSource) != 0 {
		loopAudio, err = getAudio(session, vKey, session.audioMimeType, aOpts.LoopSource)
		if err != nil {
			log.WarnK(session.id, "Load audio failed, source: %v, cause: %v", aOpts.LoopSource, err)
			audioReady = false
		}
	}
	if (len(audio) != 0 || len(loopAudio) != 0) && p.audioState == StatePlaying {
		log.WarnK(session.id, "The audio has already played, ignored, source: %v, loopSource: %v", aOpts.Source, aOpts.LoopSource)
		audioReady = false
	}
	if len(audio) == 0 && len(loopAudio) != 0 {
		audio = loopAudio
	}
	var audioSender *rtpSender
	if audioReady && (len(audio) != 0 || len(loopAudio) != 0) {
		audioSender = newRtpSender(session)
		if aOpts.Direction != mediasource.SourceCallee {
			_ = audioSender.Bind(TrackAudio, session.audioMimeType, session.streamAudioProxy.conn, rtp.TrackContext{Id: BindingCallerAudio, Addr: session.streamAudioProxy.callerAddr, PT: session.audioPayload})
		}
		if aOpts.Direction != mediasource.SourceCaller {
			_ = audioSender.Bind(TrackAudio, session.audioMimeType, session.streamAudioProxy.conn, rtp.TrackContext{Id: BindingCalleeAudio, Addr: session.streamAudioProxy.calleeAddr, PT: session.audioPayload})
		}
	}
	p.bindId = vOpts.BindId
	p.videoSender = videoSender
	go p.videoSender.SendVideo(session.id, video, LoopData(loopVideo), Process(vOpts.Process), Callback(vOpts.Callback))
	p.videoState = StatePlaying
	if audioSender != nil {
		p.audioSender = audioSender
		p.setupAudio(&aOpts)
		go p.audioSender.SendAudio(TrackAudio, audio, LoopData(loopAudio), Process(aOpts.Process), Callback(aOpts.Callback), Finalize(aOpts.FinalizeProcess))
	}
	return nil
}

func (p *rtpPlayer) startDiffVideo(session *Session, aOpts *AudioOptions, vOpts *VideoOptions) error {
	return nil
}

func (p *rtpPlayer) Rebind(session *Session, opts RebindOptions) error {
	if p.audioState == StateIdle && p.videoState == StateIdle {
		return nil
	}
	var err error
	if p.audioState != StateIdle {
		rebindCaller, rebindCallee := false, false
		if opts.AudioCallerAddr != nil {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.audioSender, rebinding{trackId: TrackCallerAudio, bindId: BindingCallerAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload, addr: opts.AudioCallerAddr})
			} else if p.direction != mediasource.SourceCallee {
				Rebind(p.audioSender, rebinding{trackId: TrackAudio, bindId: BindingCallerAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload, addr: opts.AudioCallerAddr})
			}
			rebindCaller = true
		}
		if opts.AudioCalleeAddr != nil {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.audioSender, rebinding{trackId: TrackCalleeAudio, bindId: BindingCalleeAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload, addr: opts.AudioCalleeAddr})
			} else if p.direction != mediasource.SourceCaller {
				Rebind(p.audioSender, rebinding{trackId: TrackAudio, bindId: BindingCalleeAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload, addr: opts.AudioCalleeAddr})
			}
			rebindCallee = true
		}
		if !rebindCaller {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.audioSender, rebinding{trackId: TrackCallerAudio, bindId: BindingCallerAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload})
			} else if p.direction != mediasource.SourceCallee {
				Rebind(p.audioSender, rebinding{trackId: TrackAudio, bindId: BindingCallerAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload})
			}
		}
		if !rebindCallee {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.audioSender, rebinding{trackId: TrackCalleeAudio, bindId: BindingCalleeAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload})
			} else if p.direction != mediasource.SourceCaller {
				Rebind(p.audioSender, rebinding{trackId: TrackAudio, bindId: BindingCalleeAudio, mimeType: opts.AudioMimeType, payload: opts.AudioPayload})
			}
		}
		if len(opts.AudioMimeType) != 0 {
			err = p.switchAudioSource(session, opts.AudioMimeType)
		}
	}
	if p.videoState != StateIdle {
		rebindCaller, rebindCallee := false, false
		if opts.VideoCallerAddr != nil {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.videoSender, rebinding{trackId: TrackCallerVideo, bindId: BindingCallerVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload, addr: opts.VideoCallerAddr})
			} else if p.direction != mediasource.SourceCallee {
				Rebind(p.videoSender, rebinding{trackId: TrackVideo, bindId: BindingCallerVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload, addr: opts.VideoCallerAddr})
			}
			rebindCaller = true
		}
		if opts.VideoCalleeAddr != nil {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.videoSender, rebinding{trackId: TrackCalleeVideo, bindId: BindingCalleeVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload, addr: opts.VideoCalleeAddr})
			} else if p.direction != mediasource.SourceCaller {
				Rebind(p.videoSender, rebinding{trackId: TrackVideo, bindId: BindingCalleeVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload, addr: opts.VideoCalleeAddr})
			}
			rebindCallee = true
		}
		if !rebindCaller {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.videoSender, rebinding{trackId: TrackCallerVideo, bindId: BindingCallerVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload})
			} else if p.direction != mediasource.SourceCallee {
				Rebind(p.videoSender, rebinding{trackId: TrackVideo, bindId: BindingCallerVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload})
			}
		}
		if !rebindCallee {
			if p.direction == mediasource.SourceDiff {
				Rebind(p.videoSender, rebinding{trackId: TrackCalleeVideo, bindId: BindingCalleeVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload})
			} else if p.direction != mediasource.SourceCaller {
				Rebind(p.videoSender, rebinding{trackId: TrackVideo, bindId: BindingCalleeVideo, mimeType: opts.VideoMimeType, payload: opts.VideoPayload})
			}
		}
	}
	return err
}

func (p *rtpPlayer) switchAudioSource(session *Session, targetMime string) error {
	if len(p.audioSource) == 0 && len(p.audioLoopSource) == 0 {
		return nil
	}
	sFmt := func(source string) string {
		idx := strings.LastIndex(source, DelimiterCodec)
		if idx == -1 {
			return ""
		}
		switch targetMime {
		case codec.MimeTypeAmrNB:
			return source[:idx] + "_nb.amr"
		case codec.MimeTypeAmrNBOctet:
			return source[:idx] + "_nb.amr"
		case codec.MimeTypeAmrWB:
			return source[:idx] + "_wb.amr"
		case codec.MimeTypeAmrWBOctet:
			return source[:idx] + "_wb.amr"
		case codec.MimeTypePCMA:
			return source[:idx] + "_pcma.wav"
		case codec.MimeTypePCMU:
			return source[:idx] + "_pcmu.wav"
		case codec.MimeTypeG729:
			return source[:idx] + "_bcg.g729"
		default:
			return ""
		}
	}
	sLoad := func(ctx context.Context, source string) ([][]byte, error) {
		if source == AudioMute {
			return [][]byte{muteCache[targetMime]}, nil
		}
		if source == AudioDudu {
			return duduMap[targetMime], nil
		}
		if strings.HasPrefix(source, AudioTTS) {
			return tts.Get(session.id, ":"+conf.App.MediaSource.Outbound.AppName+conf.App.MediaSource.Outbound.TTS, strings.TrimPrefix(source, AudioTTS+":"), targetMime)
		}
		var audioData [][]byte
		for _, part := range strings.Split(source, DelimiterComma) {
			audio, _, err := aCache[p.bindKey].Get(ctx, targetMime, sFmt(part))
			if err != nil {
				return nil, err
			}
			audioData = append(audioData, audio.Get(targetMime).Frames...)
		}
		return audioData, nil
	}
	// Diff audio playback
	// Temporarily not considering the scenario where video is played unilaterally with different audio
	ctx, cancelFunc := context.WithDeadline(context.Background(), time.Now().Add(time.Duration(conf.App.Cache.Cdn.AudioTimeout)*time.Second))
	defer cancelFunc()
	if p.direction == mediasource.SourceDiff {
		var err error
		var data [][]byte
		var audio, loopAudio [2][][]byte
		if len(p.audioSource) != 0 {
			for i, source := range strings.Split(p.audioSource, DelimiterSemicolon) {
				data, err = sLoad(ctx, source)
				if err != nil {
					return err
				}
				audio[i] = data
			}
		}
		if len(p.audioLoopSource) != 0 {
			for i, source := range strings.Split(p.audioLoopSource, DelimiterSemicolon) {
				data, err = sLoad(ctx, source)
				if err != nil {
					return err
				}
				loopAudio[i] = data
			}
		}
		if (len(audio[0]) == 0 && len(loopAudio[0]) == 0) || (len(audio[1]) == 0 && len(loopAudio[1]) == 0) {
			return errors.New("the audio source is empty")
		}
		if len(audio[0]) == 0 {
			audio[0] = loopAudio[0]
		}
		if len(audio[1]) == 0 {
			audio[1] = loopAudio[1]
		}
		if p.audioSender != nil {
			SwitchMediaSource(p.audioSender, avSwitch[[][]byte]{trackId: TrackCallerAudio, data: audio[0], loopData: loopAudio[0]})
			SwitchMediaSource(p.audioSender, avSwitch[[][]byte]{trackId: TrackCalleeAudio, data: audio[1], loopData: loopAudio[1]})
		}
		return nil
	}
	// Unilateral playback
	var err error
	avs := avSwitch[[][]byte]{trackId: TrackAudio}
	if len(p.audioSource) != 0 {
		avs.data, err = sLoad(ctx, p.audioSource)
		if err != nil {
			return err
		}
	}
	if len(p.audioLoopSource) != 0 {
		avs.loopData, err = sLoad(ctx, p.audioLoopSource)
		if err != nil {
			return err
		}
	}
	if len(avs.data) == 0 && len(avs.loopData) == 0 {
		return errors.New("the audio source is empty")
	}
	if len(avs.data) == 0 {
		avs.data = avs.loopData
	}
	if p.audioSender != nil {
		SwitchMediaSource(p.audioSender, avs)
	}
	return nil
}

func (p *rtpPlayer) Stop(bindId uint8) bool {
	if p.audioState == StateIdle && p.videoState == StateIdle {
		return false
	}
	if p.bindId != bindId && bindId != math.MaxUint8 {
		return false
	}
	if p.audioSender != nil {
		p.audioSender.Stop()
	}
	if p.videoSender != nil {
		p.videoSender.Stop()
	}
	p.Reset()
	return true
}

func (p *rtpPlayer) Reset() {
	p.bindId, p.direction = math.MaxUint8, 0
	p.inOutAudio, p.inOutVideo = nil, nil
	p.audioSender, p.videoSender = nil, nil
	p.audioSource, p.audioLoopSource = "", ""
	p.audioState, p.videoState = StateIdle, StateIdle
	p.priority = PriorityLocal
	p.netReady = false
}

func (p *rtpPlayer) IsAudioPlaying() bool {
	return p.audioState == StatePlaying
}

func (p *rtpPlayer) IsVideoPlaying() bool { return p.videoState == StatePlaying }

func (p *rtpPlayer) IsPlaying() bool {
	return p.audioState != StateIdle || p.videoState != StateIdle
}

func (p *rtpPlayer) IsNetPrioritized() bool {
	return p.priority == PriorityNet
}

func (p *rtpPlayer) NoticeNetReady() {
	p.netReady = true
}