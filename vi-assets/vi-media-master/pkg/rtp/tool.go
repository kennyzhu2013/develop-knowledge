package rtp

// 随机数生成器
var randomGenerator = NewMathRandomGenerator()

func NewSequence() uint16 {
	return randomGenerator.Uint16()
}

func NewTimestamp() uint32 {
	return randomGenerator.Uint32()
}

func GetPadding(packetLen int) int {
	if packetLen%4 == 0 {
		return 0
	}
	return 4 - (packetLen % 4)
}