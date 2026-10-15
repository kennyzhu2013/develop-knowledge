package proxy

import (
	"fmt"
	"net"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/mediasource"
	"vi-media/pkg/rtp"
)

type streamVideoProxy struct {
	// Data buffer, used for the rtp receiving
	buf []byte

	// The local listening address and destination address for the rtp
	conn       *net.UDPConn
	callerAddr *net.UDPAddr
	calleeAddr *net.UDPAddr

	outbound *mediasource.Outbound

	videoRecord
}

func (p *streamVideoProxy) Init() {
	p.buf = make([]byte, MaxBufferSize)
}

func (p *streamVideoProxy) Observe(session *Session) error {
	if p.conn != nil {
		return nil
	}
	conn, err := rtp.ListenUDP(fmt.Sprintf("%s:%d", conf.App.Ims.Host, session.VideoPort()))
	if err != nil {
		return err
	}
	p.conn = conn
	return nil
}

func (p *streamVideoProxy) Receive(session *Session) {
	if p.conn == nil {
		return
	}
	_ = p.conn.SetReadDeadline(time.Now().Add(time.Millisecond * time.Duration(UdpReadDeadline)))
	n, remote, err := p.conn.ReadFromUDP(p.buf)
	if err != nil || n <= 0 {
		return
	}
	log.DebugK(session.id, "StreamVideoProxy receive rtp from %v with length %v", remote, n)
	if p.callerAddr != nil && p.callerAddr.String() == remote.String() {
		session.videoStats.incCallerRtp(session)
		// Record
		err = p.MWrite(session, p.buf[:n])
		if err == nil {
			session.videoStats.CallerRecordCnt++
		} else {
			log.DebugK(session.id, "StreamVideoProxy write caller record failed: %v", err)
		}
		// Outbound stream
		if p.IsOutbound() {
			p.outbound.Send(mediasource.KindVideo, mediasource.SourceCaller, 0, p.buf[:n])
		}
		// One-way protection
		if p.calleeAddr != nil && p.callerAddr.String() != p.calleeAddr.String() {
			_, _ = p.conn.WriteToUDP(p.buf[:n], p.calleeAddr)
		}
	} else if p.calleeAddr != nil && p.calleeAddr.String() == remote.String() {
		session.videoStats.incCalleeRtp(session)
		// Record
		err = p.SWrite(session, p.buf[:n])
		if err == nil {
			session.videoStats.CalleeRecordCnt++
		} else {
			log.DebugK(session.id, "StreamVideoProxy write callee record failed: %v", err)
		}
		// Outbound stream
		if p.IsOutbound() {
			p.outbound.Send(mediasource.KindVideo, mediasource.SourceCallee, 0, p.buf[:n])
		}
		// One-way protection
		if p.callerAddr != nil && p.callerAddr.String() != p.calleeAddr.String() {
			_, _ = p.conn.WriteToUDP(p.buf[:n], p.callerAddr)
		}
	}
}

func (p *streamVideoProxy) Reset() {
	p.conn, p.outbound = nil, nil
	p.callerAddr, p.calleeAddr = nil, nil
	p.videoRecord.Reset()
}

func (p *streamVideoProxy) Close(_ *Session) {
	if p.IsOutbound() {
		_ = p.outbound.Close()
	}
	if p.conn != nil {
		_ = p.conn.Close()
	}
}

func (p *streamVideoProxy) IsOutbound() bool {
	return p.outbound != nil
}