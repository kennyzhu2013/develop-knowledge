package proxy

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"context"
	"errors"
	"fmt"
	"math"
	"sync"
	"time"
	"vi-media/internal/dto"
	"vi-media/internal/pkg/log"
	"vi-media/pkg/fsm"
)

type event struct {
	// Unique Id, sequentially increasing
	id uint8
	// The name of the event
	name string
	// The handle when the event triggered
	invoke Invoker
	// The error receiving channel when invoke failed
	err chan error
}

func (e event) String() string {
	return fmt.Sprintf("<id: %d, name: %s>", e.id, e.name)
}

type codecInfo struct {
	// The mimeType of the audio
	// audio/amr、audio/amr-octet
	// audio/amr-wb、audio/amr-wb-octet
	// audio/pcma、audio/pcmu
	audioMimeType string
	// The mimeType of the video
	// video/h264-25、video/h264-30、video/h264-50、video/h264-60
	videoMimeType string

	// The payloadType of the audio
	// 	0: pcmu
	// 	8: pcma
	// 	gte 96: amr/amr-wb
	audioPayload uint8
	// The payloadType of the video, gte 96
	videoPayload uint8
	// The payloadType of the telephone-event, gte 96
	dtmfPayload uint8
	// Indicate whether to start video observe
	videoObserve int
}

type Session struct {
	*Port
	// The codec info for session
	codecInfo
	// The audio/video player
	rtpPlayer
	// The audio/video stream proxy for receiving the rtp
	streamAudioProxy
	streamVideoProxy
	// The external real-time media sources
	streamOutbound
	// The rtp/recording statistics
	audioStats stats
	videoStats stats

	id          string
	appId       string
	callerNum   string
	calleeNum   string
	mergeConfig string
	callbackUrl string

	// voice inspection
	viConfig string
	needVI   bool
	enableVI bool
	fallback bool
	viFrames int
	viSource int
	viSeqId  uint8

	recordMode int

	createTime int64
	activeTime int64
	callTime   int64

	// The Finite-state machine that controls the transition of business status
	state *fsm.FSM
	// An event channel for receiving new requests
	event  chan *event
	vEvent chan *event
	// An event buffer for handling out-of-order requests
	curId  uint8
	events [MaxWaitEvents]*event

	ctx       context.Context
	cancel    context.CancelFunc
	exitVideo chan struct{}
	once      sync.Once
	wg        sync.WaitGroup
}

func newSession(id string, port *Port) *Session {
	session := &Session{createTime: time.Now().Unix(), id: id, Port: port, event: make(chan *event, 3)}
	session.ctx, session.cancel = context.WithCancel(context.Background())
	session.streamAudioProxy.Init()
	session.streamVideoProxy.Init()
	session.state = fsm.NewFSM(StateNone, fsm.Events{
		{
			Name: EventCreate,
			Src:  []string{StateNone},
			Dst:  StateStart,
		}, {
			Name: EventUpdate,
			Src:  []string{StateStart, StateActive},
			Dst:  StateActive,
		}, {
			Name: EventPickup,
			Src:  []string{StateActive},
			Dst:  StateInCall,
		}, {
			Name: EventUpdate,
			Src:  []string{StateInCall},
			Dst:  StateInCall,
		}, {
			Name: EventCancel,
			Src:  []string{StateStart, StateActive, StateInCall},
			Dst:  StateStopped,
		},
	}, fsm.Callbacks{})
	return session
}

func (s *Session) Create(message *dto.SessionCreate) error {
	s.appId = message.AppId
	s.curId = message.SeqId
	s.viSource = message.Direction
	s.callerNum = message.CallerNum
	s.calleeNum = message.CalleeNum
	s.recordMode = message.RecordMode
	s.mergeConfig = message.MergeConfig
	s.callbackUrl = message.CallbackUrl
	location, err := time.LoadLocation("Local")
	if err != nil {
		sessionManager.Free(s.id)
		return err
	}
	activeTime, err := time.ParseInLocation("2006-01-02 15:04:05", message.StartTime, location)
	if err != nil {
		sessionManager.Free(s.id)
		return err
	}
	s.activeTime = activeTime.Unix()
	// Start background goroutine,
	// process commands and audio/video transmission
	s.state.SetState(StateStart)
	go s.run()
	return nil
}

func (s *Session) Update(message *dto.SessionUpdate) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: message.SeqId, name: EventUpdate, invoke: s.wrap(EventUpdate, func() error { return s.update(*message) })}:
	}
	return nil
}

func (s *Session) Pickup(message *dto.SessionPickup) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: message.SeqId, name: EventPickup, invoke: s.wrap(EventPickup, func() error { return s.pickup(*message) })}:
	}
	return nil
}

func (s *Session) Stop(_ *dto.SessionStop) error {
	//select {
	//case <-s.ctx.Done():
	//	return errors.New("session closed")
	//case s.event <- &event{id: message.SeqId, name: EventCancel, invoke: func() error { return s.stop() }}:
	//}
	return s.stop()
}

func (s *Session) stop() error {
	s.once.Do(func() {
		// Shutdown the background goroutine
		s.cancel()
		// Metric
		if s.IsInCall() && (s.audioStats.CallerInCallRtpCnt == 0 || s.audioStats.CalleeInCallRtpCnt == 0) {
			incRTP(rtpTypeZero)
		}
	})
	return nil
}

func (s *Session) MediaDtmf(message *dto.MediaDtmf) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: message.SeqId, name: EventDtmf, invoke: func() error { return s.dtmf(*message) }}:
	}
	return nil
}

func (s *Session) MediaPlay(message *dto.MediaPlay) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: message.SeqId, name: EventPlay, invoke: func() error { return s.play(*message) }}:
	}
	return nil
}

func (s *Session) MediaInspect(message *dto.MediaInspect) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: message.SeqId, name: EventStream, invoke: func() error {
		s.needVI = true
		s.enableVI = true
		s.viSeqId = message.SeqId
		s.viFrames = message.Frames * 50
		s.viSource = message.Direction
		s.viConfig = message.Config
		return nil
	}}:
	}
	return nil
}

func (s *Session) MediaInspectNotice(message *dto.MediaInspectNotice) error {
	select {
	case <-s.ctx.Done():
		return nil
	case s.event <- &event{id: math.MaxUint8, name: EventStreamNotice, invoke: func() error { return s.inspectStreamNotice(*message) }}:
	}
	return nil
}

func (s *Session) MediaStream(message *dto.MediaStream) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: message.SeqId, name: EventStream, invoke: func() error { return s.stream(*message) }}:
	}
	return nil
}

func (s *Session) MediaStop(message *dto.MediaStop) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: message.SeqId, name: EventStop, invoke: func() error { return s.mStop(message.Mark) }}:
	}
	return nil
}

func (s *Session) Fallback(id uint8) error {
	select {
	case <-s.ctx.Done():
		return errors.New("session closed")
	case s.event <- &event{id: id, name: EventFallback, invoke: func() error {
		return nil
	}}:
	}
	return nil
}

func (s *Session) Reset(id string) {
	s.id = id
	s.appId, s.callerNum, s.calleeNum, s.mergeConfig = "", "", "", ""
	s.audioMimeType, s.videoMimeType = "", ""
	s.recordMode = 0
	s.audioPayload, s.videoPayload, s.dtmfPayload, s.videoObserve = 0, 0, 0, 0
	s.createTime, s.activeTime, s.callTime = time.Now().Unix(), 0, 0
	s.ctx, s.cancel = context.WithCancel(context.Background())
	s.once = sync.Once{}
	s.event = make(chan *event, 3)
	s.state.SetState(StateNone)
	s.curId = 0
	for i := 0; i < MaxWaitEvents; i++ {
		s.events[i] = nil
	}
	s.DisableVI()
	s.rtpPlayer.Reset()
	s.streamAudioProxy.Reset()
	s.streamVideoProxy.Reset()
	s.streamOutbound.Reset()
	s.audioStats.Reset()
	s.videoStats.Reset()
	s.vEvent = nil
	s.viSource = 0
	s.fallback = false
}

func (s *Session) DisableVI() {
	s.viSeqId = 0
	s.viFrames = 0
	s.viConfig = ""
	s.needVI = false
	s.enableVI = false
	s.fallback = true
}

func (s *Session) Scan() {
	timeDiff := time.Now().Unix() - s.createTime
	state := s.state.Current()
	if state == StateNone || state == StateStopped {
		return
	}
	if state == StateStart && timeDiff > 60 {
		// No requests other than create exceeds 60 seconds, released
		log.WarnK(s.id, "Session in StateStart exists for too long: %v seconds", timeDiff)
		go callback(math.MaxUint8, s.id, EventSessionRelease, s.callbackUrl, nil, fmt.Errorf("session in StateStart exists for too long: %v seconds", timeDiff))
		_ = s.stop()
		return
	}
	if state == StateActive && timeDiff > 90 {
		// Early media time exceeds 90 seconds, released
		log.WarnK(s.id, "Session in StateActive exists for too long: %v seconds", timeDiff)
		go callback(math.MaxUint8, s.id, EventSessionRelease, s.callbackUrl, nil, fmt.Errorf("session in StateActive exists for too long: %v seconds", timeDiff))
		_ = s.stop()
		return
	}
	if state == StateInCall {
		timeDiff = time.Now().Unix() - s.callTime
		if timeDiff > 14400 {
			// The session exceeds 3 hours, forced to stop
			log.WarnK(s.id, "Session exists for too long: %v seconds", timeDiff)
			go callback(math.MaxUint8, s.id, EventSessionRelease, s.callbackUrl, nil, fmt.Errorf("session exists for too long: %v seconds", timeDiff))
			_ = s.stop()
			return
		}
	}
}

func (s *Session) Async(fn func()) {
	s.wg.Add(1)
	go func() {
		defer s.wg.Done()
		fn()
	}()
}

func (s *Session) Free() {
	s.id = ""
}

func (s *Session) IsFree() bool {
	return s.id == ""
}

func (s *Session) NoRecording() bool {
	return s.recordMode == RecordModeNot
}

func (s *Session) IsRecording() bool {
	return s.recordMode == RecordModeNeed || s.recordMode == RecordModeAll
}

func (s *Session) FullRecording() bool { return s.recordMode == RecordModeAll }

func (s *Session) IsActive() bool {
	return s.state.Current() == StateActive || s.state.Current() == StateInCall
}

func (s *Session) IsInCall() bool { return s.state.Current() == StateInCall }

func (s *Session) Id() string {
	return s.id
}

func (s *Session) run() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(s.id, "Recovered from session background run with %v, stack: %v", v, process.Stack())
		}
		log.InfoK(s.id, "<<Session stopped!!!>> - <%v>", &s.audioStats)
		_ = s.stopPlay(math.MaxUint8)
		_ = s.streamOutbound.Close()
		s.wg.Wait()
		s.streamAudioProxy.Close(s)
		log.InfoK(s.id, "<<Session exit!!!>>")
		GetManager().Free(s.id)
	}()
	log.InfoK(s.id, "<<Session startup!!!>>")
	for {
		select {
		case <-s.ctx.Done():
			return
		case e := <-s.event:
			s.processEvent(e)
		default:
			s.streamAudioProxy.Receive(s)
		}
	}
}

func (s *Session) runVideo() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(s.id, "Recovered from session background run video with %v, stack: %v", v, process.Stack())
		}
		log.InfoK(s.id, "<<Session video exit!!!>>")
		s.streamVideoProxy.Close(s)
	}()
	log.InfoK(s.id, "<<Session video startup!!!>>")
	for {
		select {
		case <-s.ctx.Done():
			return
		case <-s.exitVideo:
			return
		case e := <-s.vEvent:
			_ = e.invoke()
		default:
			s.streamVideoProxy.Receive(s)
		}
	}
}

func (s *Session) processEvent(e *event) {
	log.InfoK(s.id, "Session receive an event: %v, curId: %v", e, s.curId)
	if e.id == math.MaxUint8 {
		// Internal event
		err := e.invoke()
		log.InfoK(s.id, "Session handle an internal event %v, result: %v", e, err)
		if e.err != nil {
			select {
			case e.err <- err:
			default:
			}
		}
		return
	}
	if s.curId >= e.id {
		return
	}
	if s.curId+1 < e.id {
		if e.id-s.curId-2 < MaxWaitEvents {
			log.InfoK(s.id, "Session append an event %v to the waiting queue, curId: %v", e, s.curId)
			s.events[e.id-s.curId-2] = e
		} else {
			log.WarnK(s.id, "Session ignore an event %v because the waiting queue is full, curId: %v", e, s.curId)
		}
		return
	}
	if err := e.invoke(); err != nil {
		log.WarnK(s.id, "Session handle an event %v failed: %v, curId: %v", e, err, s.curId)
	} else {
		log.InfoK(s.id, "Session handle an event %v, curId: %v", e, s.curId)
	}
	s.curId++
	for i := 0; i < MaxWaitEvents; i++ {
		if s.events[i] == nil {
			// Position correction
			for j := i + 1; j < MaxWaitEvents; j++ {
				if s.events[j] != nil {
					s.events[s.events[j].id-s.curId-2] = s.events[j]
					s.events[j] = nil
				}
			}
			break
		}
		if err := s.events[i].invoke(); err != nil {
			log.WarnK(s.id, "Session handle an event %v failed: %v, curId: %v", s.events[i], err, s.curId)
		} else {
			log.InfoK(s.id, "Session handle an event %v, curId: %v", s.events[i], s.curId)
		}
		s.events[i] = nil
		s.curId++
	}
}

func (s *Session) wrap(event string, fn Invoker) Invoker {
	return func() error {
		dstState, ok := s.state.Can(event)
		if !ok {
			return errors.New("invalid status")
		}
		if err := fn(); err != nil {
			return err
		}
		s.state.SetState(dstState)
		return nil
	}
}