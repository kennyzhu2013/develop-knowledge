package proxy

import (
	"fmt"
	"math"
	"net"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/mediasource"
	"vi-media/pkg/dtmf"
	"vi-media/pkg/format/record"
	"vi-media/pkg/rtp"
)

type streamAudioProxy struct {
	// Data buffer, used for the rtp receiving
	buf []byte

	// The timeout stats
	// Prevent the business idling
	timeout int

	// The local listening address and destination address
	conn       *net.UDPConn
	callerAddr *net.UDPAddr
	calleeAddr *net.UDPAddr

	// The last caller address in multiple media negotiations,
	// to prevent some RTPs from still being sent from the old address, resulting in unable to receive
	preCallerAddr *net.UDPAddr

	// RTP stream write handle, used for different stream processing scenarios, whether for playback or non-playback
	callerWriteStream func(*Session, *net.UDPAddr, []byte) (int, error)
	calleeWriteStream func(*Session, *net.UDPAddr, []byte) (int, error)

	// RTP stream recording handle, used for different recording processing scenarios, whether for playback or non-playback
	callerWriteRecord func(*Session, []byte) ([]byte, int, int, error)
	calleeWriteRecord func(*Session, []byte) ([]byte, int, int, error)

	dtmf     [2]*dtmf.DTMF
	outbound *mediasource.Outbound

	audioRecord
}

func (p *streamAudioProxy) Init() {
	p.buf = make([]byte, MaxBufferSize)
	p.callerWriteStream = p.write
	p.calleeWriteStream = p.write
	p.callerWriteRecord = p.MWrite
	p.calleeWriteRecord = p.SWrite
}

func (p *streamAudioProxy) Observe(session *Session) error {
	if p.conn != nil {
		return nil
	}
	conn, err := rtp.ListenUDP(fmt.Sprintf("%s:%d", conf.App.Ims.Host, session.AudioPort()))
	if err != nil {
		return err
	}
	p.conn = conn
	return nil
}

func (p *streamAudioProxy) Receive(session *Session) {
	if p.conn == nil {
		time.Sleep(time.Millisecond * time.Duration(UdpReadDeadline))
		return
	}
	_ = p.conn.SetReadDeadline(time.Now().Add(time.Millisecond * time.Duration(UdpReadDeadline)))
	n, remote, err := p.conn.ReadFromUDP(p.buf)
	if err != nil || n <= 0 {
		// Dtmf
		if p.IsDtmf(mediasource.SourceCaller) {
			_ = p.dtmf[mediasource.SourceCaller-1].Receive(nil)
		}
		if p.IsDtmf(mediasource.SourceCallee) {
			_ = p.dtmf[mediasource.SourceCallee-1].Receive(nil)
		}
		// If the cumulative timeout exceeds the MaxRtpLatency, the current call is considered unreachable,
		// and the session is released directly
		if session.IsInCall() {
			p.timeout += UdpReadDeadline
			if p.timeout > MaxRtpLatency {
				log.WarnK(session.id, "<<StreamAudioProxy timeout!!!>>")
				go callback(math.MaxUint8, session.id, EventSessionRelease, session.callbackUrl, nil, fmt.Errorf("session timeout"))
				_ = session.stop()
			}
		}
		return
	}
	p.timeout = 0
	log.DebugK(session.id, "StreamAudioProxy receive rtp from %v with length %v", remote, n)
	if p.callerAddr.String() == remote.String() || (p.preCallerAddr != nil && p.preCallerAddr.String() == remote.String()) {
		session.audioStats.incCallerRtp(session)
		// Dtmf
		if p.IsDtmf(mediasource.SourceCaller) {
			_ = p.dtmf[mediasource.SourceCaller-1].Receive(p.buf[:n])
		}
		// Record
		frames, nFrames, mFrames, wErr := p.callerWriteRecord(session, p.buf[:n])
		session.audioStats.CallerRecordCnt += nFrames + mFrames
		if wErr != nil {
			log.DebugK(session.id, "StreamAudioProxy write caller record failed: %v", wErr)
		}
		session.audioStats.CallerCompensationFrameCnt += mFrames
		// Outbound stream
		if p.IsOutbound() && session.viSource != mediasource.SourceCallee {
			p.outbound.Send(mediasource.KindAudio, mediasource.SourceCaller, mFrames, frames)
		}
		// One-way protection
		if p.callerAddr.String() != p.calleeAddr.String() {
			_, _ = p.callerWriteStream(session, p.calleeAddr, p.buf[:n])
		}
	} else {
		session.audioStats.incCalleeRtp(session)
		// Flags the core-network voice stream as ready
		session.rtpPlayer.NoticeNetReady()
		// If the core-network priority is indicated, cease the current voice playback
		if session.rtpPlayer.IsNetPrioritized() {
			_ = session.stopPlay(math.MaxUint8)
		}
		// Dtmf
		if p.IsDtmf(mediasource.SourceCallee) {
			_ = p.dtmf[mediasource.SourceCallee-1].Receive(p.buf[:n])
		}
		// Record
		frames, nFrames, mFrames, wErr := p.calleeWriteRecord(session, p.buf[:n])
		session.audioStats.CalleeRecordCnt += nFrames + mFrames
		if wErr != nil {
			log.DebugK(session.id, "StreamAudioProxy write callee record failed: %v", wErr)
		}
		session.audioStats.CalleeCompensationFrameCnt += mFrames
		// Outbound stream
		if p.IsOutbound() && session.viSource != mediasource.SourceCaller {
			p.outbound.Send(mediasource.KindAudio, mediasource.SourceCallee, mFrames, frames)
		}
		// One-way protection
		if session.IsInCall() || p.calleeAddr.String() == remote.String() {
			_, _ = p.calleeWriteStream(session, p.callerAddr, p.buf[:n])
		}
	}
	// Outbound stream startup if voice inspection needed
	if session.enableVI && !p.IsOutbound() && (record.Frames(session.audioMimeType, session.audioStats.CallerRecordCnt) >= session.viFrames ||
		record.Frames(session.audioMimeType, session.audioStats.CalleeRecordCnt) >= session.viFrames) {
		session.enableVI = false
		_ = session.inspectStream()
	}
}

func (p *streamAudioProxy) write(_ *Session, addr *net.UDPAddr, packet []byte) (int, error) {
	return p.conn.WriteToUDP(packet, addr)
}

func (p *streamAudioProxy) discardStream(session *Session, addr *net.UDPAddr, packet []byte) (int, error) {
	// Dtmf telephone-event
	if len(packet) > rtp.HeaderLength && packet[1]&0x7F == session.dtmfPayload {
		return p.conn.WriteToUDP(packet, addr)
	}
	log.DebugK(session.id, "StreamAudioProxy discard rtp from %v", addr)
	return 0, nil
}

func (p *streamAudioProxy) discardRecord(session *Session, _ []byte) ([]byte, int, int, error) {
	log.DebugK(session.id, "StreamAudioProxy discard recording")
	return nil, 0, 0, nil
}

func (p *streamAudioProxy) Reset() {
	p.timeout = 0
	p.conn, p.callerAddr, p.calleeAddr, p.preCallerAddr = nil, nil, nil, nil
	p.dtmf[0], p.dtmf[1], p.outbound = nil, nil, nil
	p.callerWriteStream = p.write
	p.calleeWriteStream = p.write
	p.callerWriteRecord = p.MWrite
	p.calleeWriteRecord = p.SWrite
	p.audioRecord.Reset()
}

func (p *streamAudioProxy) Close(session *Session) {
	if p.IsOutbound() {
		_ = p.outbound.Close()
	}
	if p.conn != nil {
		_ = p.conn.Close()
	}
	for i := range p.dtmf {
		if p.dtmf[i] != nil {
			_ = p.dtmf[i].Stop(nil)
		}
	}
	p.audioRecord.Cancel(session)
}

func (p *streamAudioProxy) IsOutbound() bool {
	return p.outbound != nil
}

func (p *streamAudioProxy) IsDtmf(source int) bool {
	var f bool
	if source == mediasource.SourceCaller || source == mediasource.SourceCallee {
		if p.dtmf[source-1] != nil && p.dtmf[source-1].IsActive() {
			f = true
		} else {
			p.dtmf[source-1] = nil
		}
	} else {
		for i := range p.dtmf {
			if p.dtmf[i] != nil && p.dtmf[i].IsActive() {
				f = true
			} else {
				p.dtmf[i] = nil
			}
		}
	}
	return f
}

func (p *streamAudioProxy) NoticeDtmf(source int) error {
	if source == mediasource.SourceCaller || source == mediasource.SourceCallee {
		if p.dtmf[source-1] != nil && p.dtmf[source-1].IsActive() {
			p.dtmf[source-1].SetMuteState()
		} else {
			p.dtmf[source-1] = nil
		}
	} else {
		for i := range p.dtmf {
			if p.dtmf[i] != nil && p.dtmf[i].IsActive() {
				p.dtmf[i].SetMuteState()
			} else {
				p.dtmf[i] = nil
			}
		}
	}
	return nil
}

func (p *streamAudioProxy) StopDtmf(source int) {
	if source == mediasource.SourceCaller || source == mediasource.SourceCallee {
		if p.dtmf[source-1] != nil {
			_ = p.dtmf[source-1].Stop(nil)
			p.dtmf[source-1] = nil
		}
	} else {
		for i := range p.dtmf {
			if p.dtmf[i] != nil {
				_ = p.dtmf[i].Stop(nil)
				p.dtmf[i] = nil
			}
		}
	}
}

func (p *streamAudioProxy) SetDtmf(source int, d *dtmf.DTMF) {
	p.dtmf[source-1] = d
}

func (p *streamAudioProxy) SetDtmfPayloadType(source, payloadType uint8) {
	if p.dtmf[source-1] != nil {
		p.dtmf[source-1].SetPayloadType(payloadType)
	}
}