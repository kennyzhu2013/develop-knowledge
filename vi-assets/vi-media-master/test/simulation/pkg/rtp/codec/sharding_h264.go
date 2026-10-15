package codec

type H264Sharding struct{}

const (
	NALUTypeNIDR   = 1
	NALUTypeIDR    = 5
	NALUTypeSEI    = 6
	NALUTypeSPS    = 7
	NALUTypePPS    = 8
	NALUTypeAUD    = 9
	NALUTypeFilter = 12
	NALUTypeStapA  = 24
	NALUTypeFua    = 28
	NALUTypeFub    = 29
)

const (
	fuaHeaderSize = 2

	nuTypeBitmask   = 0x1F
	nuRefIdcBitmask = 0x60
)

func emitNALUs(data []byte, emit func([]byte)) {
	nextInd := func(nalu []byte, start int) (indStart int, indLen int) {
		zeroCount := 0

		for i, b := range nalu[start:] {
			if b == 0 {
				zeroCount++
				continue
			} else if b == 1 {
				if zeroCount >= 2 {
					return start + i - zeroCount, zeroCount + 1
				}
			}
			zeroCount = 0
		}
		return -1, -1
	}

	nextIndStart, nextIndLen := nextInd(data, 0)
	if nextIndStart == -1 {
		emit(data)
	} else {
		for nextIndStart != -1 {
			prevStart := nextIndStart + nextIndLen
			nextIndStart, nextIndLen = nextInd(data, prevStart)
			if nextIndStart != -1 {
				emit(data[prevStart:nextIndStart])
			} else {
				// Emit until end of stream, no end indicator found
				emit(data[prevStart:])
			}
		}
	}
}

func (p *H264Sharding) Sharding(mtu uint16, payload []byte) []*Fragment {
	var payloads []*Fragment
	if len(payload) == 0 {
		return nil
	}

	emitNALUs(payload, func(nu []byte) {
		if len(nu) == 0 {
			return
		}

		nuType := nu[0] & nuTypeBitmask
		if nuType == NALUTypeAUD || nuType == NALUTypeFilter {
			return
		}
		nuRefIdc := nu[0] & nuRefIdcBitmask
		fragment := &Fragment{FType: int(nuType), KeyFrame: int(nuType) == NALUTypeIDR}

		// Single NALU
		if len(nu) <= int(mtu) {
			out := make([]byte, len(nu))
			copy(out, nu)
			fragment.Frames = append(fragment.Frames, out)
			payloads = append(payloads, fragment)
			return
		}

		// FU-A
		maxFragmentSize := int(mtu) - fuaHeaderSize

		// The FU payload consists of fragments of the payload of the fragmented
		// NAL unit so that if the fragmentation unit payloads of consecutive
		// FUs are sequentially concatenated, the payload of the fragmented NAL
		// unit can be reconstructed.  The NAL unit type octet of the fragmented
		// NAL unit is not included as such in the fragmentation unit payload,
		// 	but rather the information of the NAL unit type octet of the
		// fragmented NAL unit is conveyed in the F and NRI fields of the FU
		// indicator octet of the fragmentation unit and in the type field of
		// the FU header.  An FU payload MAY have any number of octets and MAY
		// be empty.

		nuData := nu
		// According to the RFC, the first octet is skipped due to redundant information
		nuDataIndex := 1
		nuDataLength := len(nu) - nuDataIndex
		nuDataRemaining := nuDataLength

		if minV(maxFragmentSize, nuDataRemaining) <= 0 {
			return
		}

		for nuDataRemaining > 0 {
			currentFragmentSize := minV(maxFragmentSize, nuDataRemaining)
			out := make([]byte, fuaHeaderSize+currentFragmentSize)

			// +---------------+
			// |0|1|2|3|4|5|6|7|
			// +-+-+-+-+-+-+-+-+
			// |F|NRI|  Type   |
			// +---------------+
			out[0] = NALUTypeFua
			out[0] |= nuRefIdc

			// +---------------+
			// |0|1|2|3|4|5|6|7|
			// +-+-+-+-+-+-+-+-+
			// |S|E|R|  Type   |
			// +---------------+

			out[1] = nuType
			if nuDataRemaining == nuDataLength {
				// Set start bit
				out[1] |= 1 << 7
			} else if nuDataRemaining-currentFragmentSize == 0 {
				// Set end bit
				out[1] |= 1 << 6
			}

			copy(out[fuaHeaderSize:], nuData[nuDataIndex:nuDataIndex+currentFragmentSize])
			fragment.Frames = append(fragment.Frames, out)
			fragment.FType = NALUTypeFua

			nuDataRemaining -= currentFragmentSize
			nuDataIndex += currentFragmentSize
		}
		payloads = append(payloads, fragment)
	})
	return payloads
}

func minV(a, b int) int {
	if a < b {
		return a
	}
	return b
}