package dtmf

import (
	"encoding/binary"
	"math"
)

// DTMF检测 https://www.docin.com/p-526761422.html

// |-----|-1209-|-1336-|-1477-|-1633-|
// |-697-|--1---|--2---|--3---|--A---|
// |-770-|--4---|--5---|--6---|--B---|
// |-852-|--7---|--8---|--9---|--C---|
// |-941-|--*---|--0---|--#---|--D---|

const (
	GoertzelFrameDuration = 0.045
	EnergyThreshold       = 0.004
	EnergyNoiseRatio      = 0.87
)

var (
	// Frequencies
	// DTMF信号解码时，要求允许DTMF信号有1.5%的频率偏移，具有一定的校正多普勒频移能力
	Frequencies = []int{
		687, 697, 707, // 697
		758, 770, 782, // 770
		839, 852, 865, // 852
		927, 941, 955, // 941
		1191, 1209, 1227, // 1209
		1316, 1336, 1356, // 1336
		1455, 1477, 1499, // 1477
		1609, 1633, 1647, 1657, // 1633
	}

	// Mappings DTMF信号映射
	Mappings = [][4]string{{"1", "2", "3", "A"}, {"4", "5", "6", "B"}, {"7", "8", "9", "C"}, {"*", "0", "#", "D"}}
)

type InBand struct {
	*Goertzel

	frameSize int
	// 语音采样缓冲区
	toneBuf []float32
	// 历史DTMF信号记录
	prev, prev2, prev3, prev4 string
}

func NewInBand(sampleRate int) *InBand {
	inBand := &InBand{}
	inBand.frameSize = int(math.Floor(GoertzelFrameDuration * float64(sampleRate)))
	inBand.toneBuf = make([]float32, int(math.Ceil(float64(inBand.frameSize)/3.0)))
	inBand.Goertzel = NewGoertzel(sampleRate, Frequencies)
	return inBand
}

func (i *InBand) Detect(samples []byte) (int, string) {
	if len(samples) == 0 {
		return 0, ""
	}
	cur := 0
	seq, result := "", ""
	sampleLen := len(samples)
	targetLen := len(i.toneBuf)
	for {
		pos := 0
		energy := 0.0
		if cur+(targetLen<<1) > sampleLen {
			return cur, seq
		}
		for {
			if pos >= targetLen {
				break
			}
			// 默认一个采样sign 16bit小端存储，即s16le
			sampleInt := int16(binary.LittleEndian.Uint16(samples[cur : cur+2]))
			i.toneBuf[pos] = float32(sampleInt) / (math.MaxInt16 + 1)
			// 计算总能量
			energy += math.Abs(float64(i.toneBuf[pos]))
			cur += 2
			pos++
		}
		if energy/float64(targetLen) < EnergyThreshold {
			result = ""
		} else {
			result = i.determineSignal(i.transform(i.toneBuf))
			if len(result) != 0 {
				if result == i.prev && result == i.prev2 && result == i.prev3 {
					if result != i.prev4 {
						seq += result
					}
				}
			}
		}
		i.prev4 = i.prev3
		i.prev3 = i.prev2
		i.prev2 = i.prev
		i.prev = result
	}
}

func (i *InBand) transform(samples []float32) []float32 {
	return i.filterHighestMagSquared(i.Compute(samples))
}

func (i *InBand) filterHighestMagSquared(magSquared []float32) []float32 {
	result := make([]float32, 8)
	result[0] = i.max(magSquared[0:3])
	result[1] = i.max(magSquared[3:6])
	result[2] = i.max(magSquared[6:9])
	result[3] = i.max(magSquared[9:12])
	result[4] = i.max(magSquared[12:15])
	result[5] = i.max(magSquared[15:18])
	result[6] = i.max(magSquared[18:21])
	result[7] = i.max(magSquared[21:25])
	return result
}

func (i *InBand) max(magSquared []float32) float32 {
	var maxV float32
	for _, v := range magSquared {
		if v > maxV {
			maxV = v
		}
	}
	return maxV
}

func (i *InBand) determineSignal(energies []float32) string {
	var low, high int
	var sumEnergy, maxEnergyForLow, maxEnergyForHigh float32
	for idx, energy := range energies {
		if idx < 4 {
			if energy > maxEnergyForLow {
				maxEnergyForLow = energy
				low = idx
			}
		} else {
			if energy > maxEnergyForHigh {
				maxEnergyForHigh = energy
				high = idx - 4
			}
		}
		sumEnergy += energy
	}
	if (maxEnergyForLow+maxEnergyForHigh)/sumEnergy < EnergyNoiseRatio {
		// 噪音
		return ""
	}
	return Mappings[low][high]
}