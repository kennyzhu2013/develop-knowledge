package codec

import "bytes"

const G729FrameSize = 10

var G729MuteFrame = []byte{0x60, 0x5e, 0x6F, 0x40, 0x00, 0xFA, 0xC1, 0xEF, 0x7A, 0x56}

type G729Sharding struct{}

func (p *G729Sharding) Sharding(mtu uint16, data []byte) *Fragment {
	var frames [][]byte
	if len(data) == 0 || mtu == 0 || mtu%G729FrameSize != 0 {
		return nil
	}
	for len(data) > int(mtu) {
		frame := make([]byte, mtu)
		copy(frame, data[:mtu])
		data = data[mtu:]
		frames = append(frames, frame)
	}
	// If less than MTU, pad with silence
	nFrames := len(data) / G729FrameSize
	if nFrames != 0 {
		frame := make([]byte, mtu)
		dataLen := nFrames * G729FrameSize
		copy(frame, data[:dataLen])
		copy(frame[dataLen:], bytes.Repeat(G729MuteFrame, int(mtu/G729FrameSize)-nFrames))
		frames = append(frames, frame)
	}
	return &Fragment{Frames: frames}
}

func (p *G729Sharding) ShardingFile(mtu uint16, data []byte) *Fragment {
	return p.Sharding(mtu, data)
}