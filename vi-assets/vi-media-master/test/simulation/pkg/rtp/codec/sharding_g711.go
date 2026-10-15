package codec

const MinWavHeader = 36

type G711Sharding struct{}

func (p *G711Sharding) Sharding(mtu uint16, payload []byte) *Fragment {
	var frames [][]byte
	if payload == nil || mtu == 0 {
		return nil
	}
	for len(payload) > int(mtu) {
		frame := make([]byte, mtu)
		copy(frame, payload[:mtu])
		payload = payload[mtu:]
		frames = append(frames, frame)
	}
	frame := make([]byte, len(payload))
	copy(frame, payload)
	return &Fragment{Frames: append(frames, frame)}
}

func (p *G711Sharding) ShardingFile(mtu uint16, wav []byte) *Fragment {
	// 定位流数据起始位置
	// RIFF和fmt块最小为36位
	offset := 0
	for i, size := MinWavHeader, len(wav); i < size; i++ {
		if string(wav[i:i+4]) == "data" {
			offset = i + 8
			break
		}
	}
	if offset != 0 {
		var frames [][]byte
		frameLen := int(mtu)
		for size := len(wav); offset < size; offset += frameLen {
			if offset+frameLen > size {
				frames = append(frames, wav[offset:])
				break
			}
			frames = append(frames, wav[offset:offset+frameLen])
		}
		return &Fragment{Frames: frames}
	}
	return nil
}