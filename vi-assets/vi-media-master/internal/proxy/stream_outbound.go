package proxy

import (
	"errors"
	"fmt"
	"github.com/bytedance/sonic"
	"math"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/dto"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/mediasource"
	"vi-media/pkg/concurrent/promise"
)

type streamOutbound struct {
	inner promise.Promise
}

func (s *streamOutbound) Start(session *Session, message *dto.MediaStream) {
	s.inner = mediasource.DialOutbound(":"+conf.App.MediaSource.Outbound.ClusterName+"@"+conf.App.MediaSource.Outbound.AppName+conf.App.MediaSource.Outbound.Uri,
		[]mediasource.DialOption{
			mediasource.ID(session.id),
			mediasource.ASynchronizer(session),
			mediasource.WriteTimeout(time.Duration(conf.App.MediaSource.Outbound.WriteTimeout) * time.Millisecond),
			mediasource.HandshakeTimeout(time.Duration(conf.App.MediaSource.Outbound.HandshakeTimeout) * time.Second),
			mediasource.Header(map[string][]string{
				OutboundStreamHeaderSessionId: {session.id},
				OutboundStreamHeaderParams:    {message.Config},
				OutboundStreamHeaderNotice:    {fmt.Sprintf("http://%v/call/media/inspect/notice", conf.App.ApiAddress)}},
			),
			mediasource.StreamHandler(s.onStream(session)),
		}, mediasource.WithOutboundOnSuccess(s.onSuccess(session, message)), mediasource.WithOutboundCallback(s.onCancel(session, message)),
		mediasource.WithOutboundQSize(session.viFrames/50*session.viSource+15))
}

func (s *streamOutbound) onSuccess(session *Session, message *dto.MediaStream) func(*mediasource.Outbound) error {
	return func(outbound *mediasource.Outbound) error {
		log.InfoK(session.id, "<<Session outbound stream startup!!!>>")
		select {
		case <-session.ctx.Done():
			return errors.New("session closed")
		case session.event <- &event{id: math.MaxUint8, name: EventStreamOutOnSuccess, invoke: func() error {
			session.streamAudioProxy.outbound = outbound
			if session.IsInCall() {
				outbound.Notice(mediasource.EventCalling, session.callTime)
				// Voice inspection
				if session.needVI {
					log.InfoK(session.id, "<<Session flush record to outbound stream!!!>>")
					session.audioRecord.Flush(session)
				}
			}
			outbound.Notice(mediasource.EventCodecSwitch, session.audioMimeType)
			if message.PushVideo == 1 && session.videoObserve == 1 {
				select {
				case <-session.ctx.Done():
					return errors.New("session closed")
				case session.vEvent <- &event{id: math.MaxUint8, name: EventStreamOutOnSuccess, invoke: func() error {
					session.streamVideoProxy.outbound = outbound
					return nil
				}}:
				}
			}
			return nil
		}}:
		}
		return nil
	}
}

func (s *streamOutbound) onStream(session *Session) func(int, []byte) (interface{}, error) {
	return func(msgType int, msg []byte) (interface{}, error) {
		if msgType == 1 {
			log.InfoK(session.id, "Session receive outbound stream message: %v", string(msg))
		} else {
			log.InfoK(session.id, "Session receive outbound stream binary: %v", len(msg))
		}
		// binary
		if msgType == 2 {
			return 0, nil
		}
		// text
		var eMessage struct {
			// EXCEPTION、TERMINATION
			Event    string `json:"event"`
			Message  string `json:"message"`
			Duration int    `json:"duration"`
		}
		if err := sonic.Unmarshal(msg, &eMessage); err != nil {
			return 0, fmt.Errorf("invalid outbound stream message: %v", err)
		}
		if eMessage.Event == mediasource.NoticeStateException {
			return eMessage.Duration, fmt.Errorf("disconnect due to exception with %v", eMessage.Message)
		}
		if eMessage.Event == mediasource.NoticeStateTermination {
			return eMessage.Duration, fmt.Errorf("disconnect due to termination with %v", eMessage.Message)
		}
		return 0, nil
	}
}

func (s *streamOutbound) onCancel(session *Session, message *dto.MediaStream) func(interface{}, error) {
	return func(data interface{}, err error) {
		log.InfoK(session.id, "<<Session outbound stream quit!!!>> - %v", err)
		go callback(message.SeqId, session.id, EventStreamDone, session.callbackUrl, data, err)
		// Voice inspection, fallback
		if err != nil && session.needVI {
			select {
			case <-session.ctx.Done():
				return
			case session.event <- &event{id: math.MaxUint8, name: EventStreamOutOnCancel, invoke: func() error {
				session.DisableVI()
				session.audioRecord.FallbackVI(session)
				return nil
			}}:
			}
		}
	}
}

func (s *streamOutbound) Close() error {
	if s.inner != nil {
		return s.inner.Close()
	}
	return nil
}

func (s *streamOutbound) Reset() {
	s.inner = nil
}