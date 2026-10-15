package engine

import (
	"errors"
	"math/big"
	"sync"
)

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
	// The current count
	cnt int
	// The port capacity
	capacity int
}

func NewPortPool(low, capacity int) (*PortPool, error) {
	if low&1 != 0 {
		return nil, errors.New("not even port")
	}
	return &PortPool{pool: big.NewInt(0), prev: -1, low: low, capacity: capacity}, nil
}

func (p *PortPool) Get() int {
	p.Lock()
	defer p.Unlock()
	if p.cnt >= p.capacity {
		return -1
	}
	for {
		p.prev = (p.prev + 1) % p.capacity
		if p.pool.Bit(p.prev) == 0 {
			break
		}
	}
	p.pool.SetBit(p.pool, p.prev, allocated)
	p.cnt++
	return p.low + (p.prev << 2)
}

func (p *PortPool) Put(base int) {
	p.Lock()
	defer p.Unlock()
	p.pool.SetBit(p.pool, (base-p.low)>>2, free)
	p.cnt--
}