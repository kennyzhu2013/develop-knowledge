package rtp

import (
	"context"
	"fmt"
	"net"
	"sync"
	"vi-media/pkg/rtp/codec"
)

// 最大绑定，暂定支持两个
const maxBindings = 2

// 分片处理器
var fragProcessor = [2]FragmentProcessor{audioFragment{}, videoFragment{}}

type TrackContext struct {
	Id   string
	Mime string
	SSRC uint32
	PT   uint8
	Addr *net.UDPAddr
}

type trackBinding struct {
	id   string
	ssrc uint32
	pt   uint8
	addr *net.UDPAddr
}

func (b trackBinding) String() string {
	return fmt.Sprintf("{id: %v, pt: %v, ssrc: %v, addr: %v}", b.id, b.pt, b.ssrc, b.addr.String())
}

type ITrack interface {
	Write(context.Context, []byte, ...WrittenOption) error
	WriteP(context.Context, *Packet) error
	Bind(TrackContext) error
	Unbind(TrackContext) error
	Rebind(TrackContext) error
	Bindings() string
	MimeType() string
	Kind() codec.MediaKind
	ID() string
}

type Track struct {
	id       string
	mimeType string

	seq       uint16
	timestamp uint32

	dataConn *net.UDPConn
	ctrlConn *net.UDPConn

	mu       sync.RWMutex
	bindings [maxBindings]*trackBinding
	size     int
}

func NewTrack(id, mimeType string, dataConn *net.UDPConn) *Track {
	return &Track{
		id:        id,
		mimeType:  mimeType,
		dataConn:  dataConn,
		seq:       NewSequence(),
		timestamp: NewTimestamp(),
	}
}

func (t *Track) Write(ctx context.Context, p []byte, opt ...WrittenOption) error {
	if t.dataConn == nil || t.size == 0 || len(p) == 0 {
		return nil
	}
	opts := new(writtenOptions)
	for _, o := range opt {
		o.Apply(opts)
	}
	// 分片封包
	if opts.fragment {
		// 数据分片
		fragments := codec.ShardingFile(t.mimeType, p)
		// 封包写出
		return fragProcessor[t.Kind()-1].Process(ctx, t, fragments)
	}
	// 单帧封包
	return t.writeSingle(p, opts)
}

func (t *Track) WriteP(ctx context.Context, packet *Packet) error {
	if t.dataConn == nil || t.size == 0 || packet == nil || packet.size == 0 {
		return nil
	}
	var err MultiError
	for _, binding := range t.bindings {
		if binding == nil {
			continue
		}
		packet.SetSequence(t.seq)
		packet.SetSSRC(binding.ssrc)
		packet.SetPayloadType(binding.pt)
		_, wErr := t.dataConn.WriteTo(packet.buf[:packet.size], binding.addr)
		if wErr != nil {
			err = append(err, wErr)
		}
	}
	t.seq++
	if len(err) == 0 {
		return nil
	}
	return err
}

func (t *Track) writeSingle(p []byte, opts *writtenOptions) error {
	// 获取媒体参数
	avParameters, ok := codec.Find(t.mimeType)
	if !ok {
		return codec.ErrUnknownType
	}
	var err MultiError
	// 初始化数据包
	packet := newPacket(t.seq, opts.marker, t.timestamp)
	packet.SetPayload(p)
	defer FreePacket(packet)
	for _, binding := range t.bindings {
		if binding == nil {
			continue
		}
		packet.SetSSRC(binding.ssrc)
		packet.SetPayloadType(binding.pt)
		_, wErr := t.dataConn.WriteTo(packet.buf[:packet.size], binding.addr)
		if wErr != nil {
			err = append(err, wErr)
		}
	}
	t.seq++
	if !opts.sameTs {
		t.timestamp += avParameters.ClockRate / avParameters.FrameRate
	}
	if len(err) == 0 {
		return nil
	}
	return err
}

func (t *Track) Bind(ctx TrackContext) error {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.size == maxBindings {
		return errReachedBindingLimit
	}
	for i := range t.bindings {
		if t.bindings[i] != nil {
			continue
		}
		t.bindings[i] = &trackBinding{
			id:   ctx.Id,
			ssrc: ctx.SSRC,
			pt:   ctx.PT,
			addr: ctx.Addr,
		}
		if t.bindings[i].ssrc == 0 {
			t.bindings[i].ssrc = randomGenerator.Uint32()
		}
		t.size++
		break
	}
	return nil
}

func (t *Track) Unbind(ctx TrackContext) error {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.size == 0 {
		return nil
	}
	for i := range t.bindings {
		if t.bindings[i] == nil {
			continue
		}
		if t.bindings[i].id == ctx.Id {
			t.bindings[i] = nil
			t.size--
			break
		}
	}
	return nil
}

func (t *Track) Rebind(ctx TrackContext) error {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.size == 0 {
		return nil
	}
	for i := range t.bindings {
		if t.bindings[i] == nil {
			continue
		}
		if t.bindings[i].id != ctx.Id {
			continue
		}
		if ctx.Addr != nil && ctx.Addr.String() != t.bindings[i].addr.String() {
			t.bindings[i].addr = ctx.Addr
		}
		if ctx.PT != 0 && ctx.PT != t.bindings[i].pt {
			t.bindings[i].pt = ctx.PT
		}
	}
	if len(ctx.Mime) != 0 {
		t.mimeType = ctx.Mime
	}
	return nil
}

func (t *Track) Bindings() string {
	return fmt.Sprintf("bindings: %v:%v", t.bindings[0], t.bindings[1])
}

func (t *Track) MimeType() string {
	return t.mimeType
}

func (t *Track) Kind() codec.MediaKind {
	return codec.KindForMimeType(t.mimeType)
}

func (t *Track) ID() string {
	return t.id
}