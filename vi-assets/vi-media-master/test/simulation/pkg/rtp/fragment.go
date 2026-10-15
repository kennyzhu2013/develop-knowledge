package rtp

import (
	"context"
	"simulation/pkg/rtp/codec"
	"time"
)

const AudioInterval = 20 * time.Millisecond

type FragmentProcessor interface {
	Process(context.Context, *Track, []*codec.Fragment) error
}

type audioFragment struct{}

func (f audioFragment) Process(ctx context.Context, t *Track, fg []*codec.Fragment) error {
	// 获取媒体参数
	avParameters, ok := codec.Find(t.mimeType)
	if !ok {
		return codec.ErrUnknownType
	}
	var err MultiError
	// 初始化数据包
	packet := NewPacket()
	defer FreePacket(packet)
	// 数据包写出
	ticker := time.NewTicker(AudioInterval)
	defer ticker.Stop()
	for _, frame := range fg[0].Frames {
		select {
		case <-ctx.Done():
			if len(err) == 0 {
				return nil
			}
			return err
		case <-ticker.C:
			packet.SetPayload(frame)
			packet.SetSequence(t.seq)
			packet.SetTimestamp(t.timestamp)
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
			t.timestamp += avParameters.ClockRate / avParameters.FrameRate
		}
	}
	if len(err) == 0 {
		return nil
	}
	return err
}

type videoFragment struct{}

func (f videoFragment) Process(ctx context.Context, t *Track, fragments []*codec.Fragment) error {
	// 获取媒体参数
	avParameters, ok := codec.Find(t.mimeType)
	if !ok {
		return codec.ErrUnknownType
	}
	var err MultiError
	// 初始化数据包
	packet := newPacket(t.seq, false, t.timestamp)
	defer FreePacket(packet)
	// 数据包写出
	ticker := time.NewTicker(time.Duration(1000/avParameters.FrameRate) * time.Millisecond)
	defer ticker.Stop()
	for _, fg := range fragments {
		select {
		case <-ctx.Done():
			if len(err) == 0 {
				return nil
			}
			return err
		default:
			if fg.FType == codec.NALUTypeNIDR || fg.FType == codec.NALUTypeIDR || fg.FType == codec.NALUTypeSEI || fg.FType == codec.NALUTypeSPS || fg.FType == codec.NALUTypePPS {
				// Non-IDR帧设置marker为true
				packet.SetMarker(fg.FType == codec.NALUTypeNIDR)
				// 设置负载
				packet.SetPayload(fg.Frames[0])
				// 数据包写出
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
				if fg.FType == codec.NALUTypeNIDR || fg.FType == codec.NALUTypeIDR {
					t.timestamp += avParameters.ClockRate / avParameters.FrameRate
					<-ticker.C
				}
			} else if fg.FType == codec.NALUTypeFua {
				for idx := range fg.Frames {
					packet.SetSequence(t.seq)
					packet.SetPayload(fg.Frames[idx])
					packet.SetMarker(idx == len(fg.Frames)-1)
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
				}
				t.timestamp += avParameters.ClockRate / avParameters.FrameRate
				<-ticker.C
			}
		}
	}
	if len(err) == 0 {
		return nil
	}
	return err
}