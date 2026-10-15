package dtmf

import (
	"math"
)

// Goertzel 格兹尔算法
// 一个有效率的方式来估计部分区域的离散傅立叶转换，广泛的运用在数字电话中的的双音多频信号
type Goertzel struct {
	// 采样率
	sampleRate int
	// 目标频率
	frequencies []int
	// 目标频率对应能量系数
	coefficients []float32
}

func NewGoertzel(sampleRate int, targetFrequencies []int) *Goertzel {
	g := &Goertzel{sampleRate: sampleRate, frequencies: targetFrequencies}
	g.computeCoefficients()
	return g
}

func (g *Goertzel) computeCoefficients() {
	g.coefficients = make([]float32, len(g.frequencies))
	for i, frequency := range g.frequencies {
		// R：采样率、f：目标频率、N：检测采样数、K：检测段包含目标频率的完整周期个数K
		// K = (N/R)*f
		// Ω：每个采样在一个目标频率一个周期中所占的弧度
		// Ω = 2πK/N
		// C：系数
		// C = 2cos(ω) = 2cos(2πK/N)
		g.coefficients[i] = float32(2.0 * math.Cos(2.0*math.Pi*float64(frequency)/float64(g.sampleRate)))
	}
}

func (g *Goertzel) Compute(samples []float32) []float32 {
	magSquared := make([]float32, len(g.frequencies))
	for i := range g.frequencies {
		var q0, q1, q2 float32
		// 对每个采样进行能量值计算
		for _, sample := range samples {
			q0 = (g.coefficients[i] * q1) - q2 + sample
			q2 = q1
			q1 = q0
		}
		// 计算能量
		// P = Q1*Q1+Q2*Q2-C*Q1*Q2
		magSquared[i] = q1*q1 + q2*q2 - q1*q2*g.coefficients[i]
	}
	return magSquared
}

func (g *Goertzel) SampleRate() int {
	return g.sampleRate
}