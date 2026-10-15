package codec

var (
	CMR           = []byte{1, 1, 1, 1}
	AmrNbFrameLen = []int{95, 103, 118, 134, 148, 159, 204, 244, 39}
	AmrWbFrameLen = []int{18, 23, 33, 37, 41, 47, 51, 59, 61, 0, 0, 0, 0, 0, 0, 0}
)

type AmrNbSharding struct{}

func (p *AmrNbSharding) Sharding(payload []byte) *Fragment {
	var frames [][]byte
	payload = Bytes2Bits(payload)
	for index, size := 0, len(payload); index < size; {
		frame := make([]byte, 0)
		frame = append(frame, CMR...)
		frame = append(frame, 0)
		frame = append(frame, payload[index+1:index+6]...)
		frameLen := AmrNbFrameLen[int((payload[index+1]<<3)+(payload[index+2]<<2)+(payload[index+3]<<1)+payload[index+4])]
		frame = append(frame, payload[index+8:index+frameLen+8]...)
		for num := 8 - len(frame)%8; 0 < num && num < 8; num-- {
			frame = append(frame, 0)
		}
		frames = append(frames, Bits2Bytes(frame))
		index += 8 + frameLen
		for num := 8 - frameLen%8; 0 < num && num < 8; num-- {
			index += 1
		}
	}
	return &Fragment{Frames: frames}
}

func (p *AmrNbSharding) ShardingFile(payload []byte) *Fragment {
	return p.Sharding(payload[6:])
}

type AmrNbOctetSharding struct{}

func (p *AmrNbOctetSharding) Sharding(payload []byte) *Fragment {
	var frames [][]byte
	payload = Bytes2Bits(payload)
	for index := 0; index < len(payload); {
		frame := make([]byte, 0)
		frame = append(frame, CMR...)
		frame = append(frame, 0, 0, 0, 0)
		frame = append(frame, 0)
		frame = append(frame, payload[index+1:index+6]...)
		frame = append(frame, 0, 0)
		frameLen := AmrNbFrameLen[int((payload[index+1]<<3)+(payload[index+2]<<2)+(payload[index+3]<<1)+payload[index+4])]
		frame = append(frame, payload[index+8:index+frameLen+8]...)
		for num := 8 - frameLen%8; 0 < num && num < 8; num-- {
			frame = append(frame, 0)
			index += 1
		}
		frames = append(frames, Bits2Bytes(frame))
		index += 8 + frameLen
	}
	return &Fragment{Frames: frames}
}

func (p *AmrNbOctetSharding) ShardingFile(payload []byte) *Fragment {
	return p.Sharding(payload[6:])
}

type AmrWbSharding struct{}

func (p *AmrWbSharding) Sharding(payload []byte) *Fragment {
	var frame []byte
	var frames [][]byte
	for current, length := 0, len(payload); current < length; {
		index := 0
		ft := payload[current] >> 3
		next := current + AmrWbFrameLen[ft]
		frame = make([]byte, AmrWbFrameLen[ft])
		frame[index] = (8 << 4) | (payload[current] >> 4)
		frame[index+1] = (payload[current] << 4) | (payload[current+1] >> 2)
		index += 2
		for current += 2; current < next; current++ {
			frame[index] = (payload[current-1] << 6) | (payload[current] >> 2)
			index++
		}
		frames = append(frames, frame)
	}
	return &Fragment{Frames: frames}
}

func (p *AmrWbSharding) ShardingFile(payload []byte) *Fragment {
	return p.Sharding(payload[9:])
}

type AmrWbOctetSharding struct{}

func (p *AmrWbOctetSharding) Sharding(payload []byte) *Fragment {
	var frame []byte
	var frames [][]byte
	speechHeader := 8 << 4
	for current, length := 0, len(payload); current < length; {
		ft := payload[current] >> 3
		next := current + AmrWbFrameLen[ft]
		frame = make([]byte, AmrWbFrameLen[ft]+1)
		frame[0] = byte(speechHeader)
		copy(frame[1:], payload[current:next])
		frames = append(frames, frame)
		current = next
	}
	return &Fragment{Frames: frames}
}

func (p *AmrWbOctetSharding) ShardingFile(payload []byte) *Fragment {
	return p.Sharding(payload[9:])
}

func Bytes2Bits(data []byte) []byte {
	bin := make([]byte, 0)
	for _, v := range data {
		for i := 0; i < 8; i++ {
			move := uint(7 - i)
			bin = append(bin, (v>>move)&1)
		}
	}
	return bin
}

func Bits2Bytes(data []byte) []byte {
	bytes := make([]byte, 0)
	for i := 0; i < len(data); i += 8 {
		bytes = append(bytes, (data[i]<<7)+(data[i+1]<<6)+(data[i+2]<<5)+(data[i+3]<<4)+(data[i+4]<<3)+(data[i+5]<<2)+(data[i+6]<<1)+(data[i+7]))
	}
	return bytes
}