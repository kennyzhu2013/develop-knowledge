package proxy

import (
	"errors"
	"math"
	"strings"
	"time"
	"vi-media/internal/dto"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/mediasource"
	"vi-media/pkg/dtmf"
)

type Invoker func() error

func (s *Session) pickup(message dto.SessionPickup) error {
	callTime, err := time.Parse("2006-01-02 15:04:05", message.StartTime)
	if err == nil {
		s.callTime = callTime.Unix()
	}
	_ = s.stopPlay(math.MaxUint8)
	s.streamAudioProxy.audioRecord.Update(s, false)
	// Outbound stream
	if s.streamAudioProxy.outbound != nil {
		s.streamAudioProxy.outbound.Notice(mediasource.EventCalling, s.callTime)
	}
	return nil
}

func (s *Session) update(message dto.SessionUpdate) error {
	log.InfoK(s.id, "Session's current stats, %v", &s.audioStats)
	err := s.streamAudioProxy.Observe(s)
	if err != nil {
		return err
	}
	if s.streamAudioProxy.IsDtmf(mediasource.SourceCaller) {
		s.SetDtmfPayloadType(mediasource.SourceCaller, message.AudioDtmfPayload)
	}
	if s.streamAudioProxy.IsDtmf(mediasource.SourceCallee) {
		s.SetDtmfPayloadType(mediasource.SourceCallee, message.AudioDtmfPayload)
	}
	if s.rtpPlayer.IsPlaying() {
		s.rebindProcess(&message)
	}
	// Video processing
	if s.videoObserve == 0 && message.VideoObserve == 1 {
		if message.VideoCallerAddr == nil && message.VideoCalleeAddr == nil {
			log.WarnK(s.id, "Session start video observe failed: empty videoAddr")
			return nil
		}
		if err = s.streamVideoProxy.Observe(s); err != nil {
			log.WarnK(s.id, "Session start video observe failed: %v", err)
		} else {
			s.vEvent = make(chan *event, 2)
			s.exitVideo = make(chan struct{})
			// Start video background goroutine,
			// process commands and video transmission
			log.InfoK(s.id, "<<Session active!!!>> - start video observing")
			s.Async(s.runVideo)
		}
	} else if s.videoObserve == 1 && message.VideoObserve == 0 {
		// Close video background goroutine
		close(s.exitVideo)
	} else if message.VideoCallerAddr != nil || message.VideoCalleeAddr != nil {
		if err = s.streamVideoProxy.Observe(s); err != nil {
			log.WarnK(s.id, "Session start video observe failed: %v", err)
		}
	}
	s.videoMimeType = message.VideoCode
	s.videoPayload = message.VideoPayload
	s.videoObserve = message.VideoObserve
	s.streamVideoProxy.callerAddr = message.VideoCallerAddr
	s.streamVideoProxy.calleeAddr = message.VideoCalleeAddr
	// Create video recording when in call
	if s.videoObserve == 1 && s.IsInCall() {
		s.vEvent <- &event{invoke: func() error {
			s.streamVideoProxy.videoRecord.Update(s)
			return nil
		}}
	}
	// Audio processing
	audioMimeType := s.audioMimeType
	s.audioMimeType = message.AudioCode
	s.audioPayload = message.AudioPayload
	s.dtmfPayload = message.AudioDtmfPayload
	s.streamAudioProxy.callerAddr = message.AudioCallerAddr
	s.streamAudioProxy.calleeAddr = message.AudioCalleeAddr
	if s.audioMimeType == audioMimeType {
		log.WarnK(s.id, "Session refresh audio codec, unchanged mimeType: %v, ignored", audioMimeType)
		return nil
	}
	log.InfoK(s.id, "Session refresh audio codec, mimeType changed to %v", s.audioMimeType)
	s.streamAudioProxy.audioRecord.Update(s, !s.IsInCall())
	// Refresh audio mimeType
	if s.streamAudioProxy.IsOutbound() {
		s.streamAudioProxy.outbound.Notice(mediasource.EventCodecSwitch, s.audioMimeType)
	}
	return nil
}

func (s *Session) dtmf(message dto.MediaDtmf) error {
	if s.IsDtmf(message.Direction) {
		return errors.New("dtmf has been activated")
	}
	// Audio playback If the audio exists
	seqId := message.SeqId
	source := message.Direction
	muteState := message.AudioSource == ""
	if !muteState {
		options := AudioOptions{
			BindId:     seqId,
			Source:     message.AudioSource,
			LoopSource: message.AudioLoopSource,
			Direction:  message.Direction,
			Process:    AudioProcess(s, message.RecordMode, message.Direction),
			Callback: func() {
				s.event <- &event{id: math.MaxUint8, name: EventDtmfDone, invoke: func() error { return s.stopPlay(seqId) }}
			},
			FinalizeProcess: func() {
				s.event <- &event{id: math.MaxUint8, name: EventDtmfNotice, invoke: func() error { return s.streamAudioProxy.NoticeDtmf(source) }}
			},
		}
		if err := s.rtpPlayer.StartAudio(s, options); err != nil {
			log.WarnK(s.id, "Session start audio for dtmf failed: %v", err)
			if len(message.AudioDefaultSource) == 0 {
				muteState = true
			} else {
				options.Source = message.AudioDefaultSource
				if err = s.rtpPlayer.StartAudio(s, options); err != nil {
					log.WarnK(s.id, "Session start audio for dtmf failed: %v", err)
					muteState = true
				}
			}
		}
	}
	// Audio playback successful
	// Set stream and recording processing mode
	if !muteState {
		Discard(s, message.Direction, message.RecordMode)
	}
	// Init and start
	init := func(source int) {
		var detector dtmf.Detector
		if s.dtmfPayload > 0 {
			detector, _ = dtmf.NewDetector2833(s.dtmfPayload)
		} else {
			detector, _ = dtmf.NewDetectorInBand(s.audioMimeType, dtmf.WithSessionId(s.id), dtmf.WithLogger(log.Log))
		}
		d := dtmf.New(detector, dtmf.Mode(message.Mode), dtmf.TimingMode(message.TimingMode), dtmf.Timeout(message.Timeout), dtmf.IntervalTimeout(message.IntervalTimeout),
			dtmf.Len(message.ExpectLen), dtmf.EndSign(message.EndSign), dtmf.BackSign(message.BackSign), dtmf.MuteState(muteState), dtmf.EndCallback(func(data string, err error) {
				_ = s.stopPlay(seqId)
				result := &dtmfResult{Source: source}
				if err != nil {
					result.EndCause = "Timeout"
				} else {
					items := strings.Split(data, ":")
					if len(items) == 2 {
						result.Digit = items[0]
						result.EndCause = items[1]
					} else if len(items) == 1 {
						result.Digit = items[0]
						result.EndCause = "HangUp"
					} else {
						result.EndCause = "HangUp"
					}
				}
				go callback(seqId, s.id, EventDtmfDone, s.callbackUrl, result, err)
			}))
		_ = d.Start()
		s.streamAudioProxy.dtmf[source-1] = d
	}
	if message.Direction != mediasource.SourceCallee {
		init(mediasource.SourceCaller)
	}
	if message.Direction != mediasource.SourceCaller {
		init(mediasource.SourceCallee)
	}
	return nil
}

func (s *Session) play(message dto.MediaPlay) error {
	seqId := message.SeqId
	period := message.Period
	aOpts := AudioOptions{
		BindId:     seqId,
		Source:     message.AudioSource,
		LoopSource: message.AudioLoopSource,
		MimeType:   message.AudioCode,
		Payload:    message.AudioPayload,
		Direction:  message.Direction,
		Process:    AudioProcess(s, message.RecordMode, message.Direction),
	}
	if len(message.VideoSource) == 0 && len(message.VideoLoopSource) == 0 {
		// Audio playback
		aOpts.BindKey = aKey
		aOpts.Callback = func() {
			s.event <- &event{id: math.MaxUint8, name: EventAudioDone, invoke: func() error {
				go callback(seqId, s.id, EventAudioDone, s.callbackUrl, period, nil)
				return s.stopPlay(seqId)
			}}
		}
		if err := s.rtpPlayer.StartAudio(s, aOpts); err != nil {
			go callback(seqId, s.id, EventAudioDone, s.callbackUrl, period, err)
			return err
		}
	} else {
		// Video playback
		if message.Direction != mediasource.SourceCallee && s.streamVideoProxy.callerAddr == nil {
			err := errors.New("invalid video caller address")
			go callback(seqId, s.id, EventVideoDone, s.callbackUrl, period, err)
			return err
		}
		if message.Direction != mediasource.SourceCaller && s.streamVideoProxy.calleeAddr == nil {
			err := errors.New("invalid video callee address")
			go callback(seqId, s.id, EventVideoDone, s.callbackUrl, period, err)
			return err
		}
		aOpts.BindKey = vKey
		vOpts := VideoOptions{
			BindId:     seqId,
			Source:     message.VideoSource,
			LoopSource: message.VideoLoopSource,
			Direction:  message.Direction,
			Process:    VideoProcess(s, message.RecordMode, message.Direction),
			Callback: func() {
				s.event <- &event{id: math.MaxUint8, name: EventVideoDone, invoke: func() error {
					go callback(seqId, s.id, EventVideoDone, s.callbackUrl, period, nil)
					return s.stopPlay(seqId)
				}}
			},
		}
		if err := s.rtpPlayer.StartVideo(s, aOpts, vOpts); err != nil {
			go callback(seqId, s.id, EventVideoDone, s.callbackUrl, period, err)
			return err
		}
	}
	if s.rtpPlayer.IsAudioPlaying() {
		Discard(s, message.Direction, message.RecordMode)
	}
	return nil
}

func (s *Session) stream(message dto.MediaStream) error {
	// Outbound processing
	s.streamOutbound.Start(s, &message)
	return nil
}

func (s *Session) inspectStream() error {
	s.streamOutbound.Start(s, &dto.MediaStream{SessionId: s.id, Config: s.viConfig, SeqId: s.viSeqId})
	return nil
}

func (s *Session) inspectStreamNotice(message dto.MediaInspectNotice) error {
	if s.streamAudioProxy.IsOutbound() {
		s.streamAudioProxy.outbound.Sema(mediasource.Event(message.Event), message.Data, message.Message)
	}
	return nil
}

func (s *Session) stopPlay(bindId uint8) error {
	if s.rtpPlayer.Stop(bindId) {
		s.streamAudioProxy.callerWriteStream = s.streamAudioProxy.write
		s.streamAudioProxy.calleeWriteStream = s.streamAudioProxy.write
		s.streamAudioProxy.callerWriteRecord = s.streamAudioProxy.MWrite
		s.streamAudioProxy.calleeWriteRecord = s.streamAudioProxy.SWrite
	}
	return nil
}

func (s *Session) mStop(mark string) error {
	if mark == "DTMF" {
		if s.streamAudioProxy.IsDtmf(mediasource.SourceEntire) {
			s.streamAudioProxy.StopDtmf(mediasource.SourceEntire)
		}
	} else {
		_ = s.stopPlay(math.MaxUint8)
	}
	return nil
}

func (s *Session) rebindProcess(message *dto.SessionUpdate) {
	var opts RebindOptions
	if s.streamAudioProxy.callerAddr != message.AudioCallerAddr {
		opts.AudioCallerAddr = message.AudioCallerAddr
	}
	if s.streamAudioProxy.calleeAddr != message.AudioCalleeAddr {
		opts.AudioCalleeAddr = message.AudioCalleeAddr
	}
	if s.audioPayload != message.AudioPayload {
		opts.AudioPayload = message.AudioPayload
	}
	if s.audioMimeType != message.AudioCode {
		opts.AudioMimeType = message.AudioCode
	}
	if err := s.rtpPlayer.Rebind(s, opts); err != nil {
		log.WarnK(s.id, "Session rebind failed: %v", err)
	}
}

func AudioProcess(session *Session, mode, target int) func(int, []byte) error {
	return func(source int, rtp []byte) error {
		if !session.IsRecording() || mode == RecordNone || mode == RecordVideo {
			return nil
		}
		if target != mediasource.SourceCallee && source != mediasource.SourceCallee {
			_, _, mFrames, err := session.streamAudioProxy.audioRecord.MWrite(session, rtp)
			if err == nil {
				session.audioStats.incCallerPlayRecord()
			}
			session.audioStats.CallerCompensationFrameCnt += mFrames
			return err
		}
		if target != mediasource.SourceCaller && source != mediasource.SourceCaller {
			_, _, mFrames, err := session.streamAudioProxy.audioRecord.SWrite(session, rtp)
			if err == nil {
				session.audioStats.incCalleePlayRecord()
			}
			session.audioStats.CalleeCompensationFrameCnt += mFrames
			return err
		}
		return nil
	}
}

func VideoProcess(session *Session, mode, target int) func(int, []byte) error {
	return func(source int, rtp []byte) error {
		if !session.IsRecording() || mode == RecordNone || mode == RecordAudio {
			return nil
		}
		if target != mediasource.SourceCallee && source != mediasource.SourceCallee {
			err := session.streamVideoProxy.videoRecord.MWrite(session, rtp)
			if err == nil {
				session.videoStats.incCallerPlayRecord()
			}
			return err
		}
		if target != mediasource.SourceCaller && source != mediasource.SourceCaller {
			err := session.streamVideoProxy.videoRecord.SWrite(session, rtp)
			if err == nil {
				session.videoStats.incCalleePlayRecord()
			}
			return err
		}
		return nil
	}
}

func Discard(session *Session, source, mode int) {
	if source != mediasource.SourceCallee {
		session.streamAudioProxy.calleeWriteStream = session.streamAudioProxy.discardStream
		if session.IsRecording() && (mode == RecordAudio || mode == RecordBoth) {
			session.streamAudioProxy.calleeWriteRecord = session.streamAudioProxy.discardRecord
		}
	}
	if source != mediasource.SourceCaller {
		session.streamAudioProxy.callerWriteStream = session.streamAudioProxy.discardStream
		if session.IsRecording() && (mode == RecordAudio || mode == RecordBoth) {
			session.streamAudioProxy.callerWriteRecord = session.streamAudioProxy.discardRecord
		}
	}
}