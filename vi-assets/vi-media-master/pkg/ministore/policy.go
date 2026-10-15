package ministore

type Volume struct {
	// 存储根路径
	Path string
	// 权重
	Weight int
	// 当前权重
	currentWeight int
}

// LBPolicy 负载策略，用于IO存储选择
type LBPolicy interface {
	Choose() string
}

// WeightedRoundRobinLBPolicy 加权轮询策略
type WeightedRoundRobinLBPolicy struct {
	volumes []Volume
}

func NewWeightedRoundRobinLBPolicy(volumes []Volume) *WeightedRoundRobinLBPolicy {
	return &WeightedRoundRobinLBPolicy{volumes: volumes}
}

func (lb *WeightedRoundRobinLBPolicy) Choose() string {
	vIdx := -1
	var weight int
	for idx := range lb.volumes {
		weight += lb.volumes[idx].Weight
		lb.volumes[idx].currentWeight += lb.volumes[idx].Weight
		if vIdx == -1 || lb.volumes[idx].currentWeight > lb.volumes[vIdx].currentWeight {
			vIdx = idx
		}
	}
	lb.volumes[vIdx].currentWeight -= weight
	return lb.volumes[vIdx].Path
}

func (lb *WeightedRoundRobinLBPolicy) Update(path string, weight int) {
	for idx := range lb.volumes {
		if lb.volumes[idx].Path == path {
			lb.volumes[idx].Weight = weight
			break
		}
	}
}