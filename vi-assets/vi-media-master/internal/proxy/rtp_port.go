package proxy

import (
	"errors"
	"math/big"
	"sync"
)

type Port struct {
	// The audio/video rtp port
	audioPort int
	videoPort int
	// The audio/video rtcp port
	audioCtrlPort int
	videoCtrlPort int
}

func (p *Port) AudioPort() int {
	return p.audioPort
}

func (p *Port) VideoPort() int {
	return p.videoPort
}

func (p *Port) AudioCtrlPort() int {
	return p.audioCtrlPort
}

func (p *Port) VideoCtrlPort() int {
	return p.videoCtrlPort
}

const (
	free      = 0
	allocated = 1
)

type PortPool struct {
	sync.Mutex
	// The identifier pool of the allocated port
	pool *big.Int
	// The previous allocated location
	prev int
	// The lowest port, must be even
	low int
	// The port capacity
	capacity int
}

func InitPortPool(low, capacity int) (*PortPool, error) {
	if low&1 != 0 {
		return nil, errors.New("not even port")
	}
	return &PortPool{pool: big.NewInt(0), prev: -1, low: low, capacity: capacity}, nil
}

func (p *PortPool) Get() (*Port, error) {
	p.Lock()
	defer p.Unlock()
	for {
		p.prev = (p.prev + 1) % p.capacity
		if p.pool.Bit(p.prev) == 0 {
			break
		}
	}
	p.pool.SetBit(p.pool, p.prev, allocated)
	base := p.low + (p.prev << 2)
	return &Port{audioPort: base, audioCtrlPort: base + 1, videoPort: base + 2, videoCtrlPort: base + 3}, nil
}

func (p *PortPool) Put(base int) {
	p.Lock()
	defer p.Unlock()
	p.pool.SetBit(p.pool, (base-p.low)>>2, free)
}