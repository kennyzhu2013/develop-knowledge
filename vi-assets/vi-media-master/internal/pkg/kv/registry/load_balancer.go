package registry

import (
	"cmic.chinamobile.com/ziyan/go-sdk/registry"
	"errors"
	"fmt"
	"sync"
	"sync/atomic"
	"time"
	"vi-media/pkg/hash"
)

type LoadBalancerType string

const (
	// LoadBalancerTypeHashRing Consistent Hashing
	LoadBalancerTypeHashRing LoadBalancerType = "HASH_RING"
	// LoadBalancerTypeRoundRobin RoundRobin
	LoadBalancerTypeRoundRobin LoadBalancerType = "ROUND_ROBIN"
)

type LoadBalancer interface {
	GetNode(*registry.Instance, string) (string, error)
}

type RoundRobinLoadBalancer struct {
	mu  sync.Mutex
	idx int
}

func NewRoundRobinLoadBalancer() *RoundRobinLoadBalancer {
	return &RoundRobinLoadBalancer{idx: -1}
}

func (lb *RoundRobinLoadBalancer) GetNode(instance *registry.Instance, _ string) (string, error) {
	lb.mu.Lock()
	defer lb.mu.Unlock()
	lb.idx++
	if lb.idx >= len(instance.Nodes) {
		lb.idx = 0
	}
	return fmt.Sprintf("%v:%v", instance.Nodes[lb.idx].Host, instance.Nodes[lb.idx].Port), nil
}

type ConsistentHashLoadBalancer struct {
	hr  *hash.Consistent
	ver uint64
}

func NewConsistentHashLoadBalancer() *ConsistentHashLoadBalancer {
	return &ConsistentHashLoadBalancer{hr: hash.NewConsistent()}
}

func (lb *ConsistentHashLoadBalancer) GetNode(instance *registry.Instance, key string) (string, error) {
	current := atomic.LoadUint64(&lb.ver)
	if current < instance.Round && atomic.CompareAndSwapUint64(&lb.ver, current, instance.Round) {
		nodes := make([]string, len(instance.Nodes))
		for i := range instance.Nodes {
			nodes[i] = fmt.Sprintf("%v:%v", instance.Nodes[i].Host, instance.Nodes[i].Port)
		}
		lb.hr.Set(nodes)
	}
	n, err := lb.hr.Get(key)
	if err != nil {
		if !errors.Is(err, hash.ErrEmptyCircle) {
			return "", err
		}
		for atomic.LoadUint64(&lb.ver) < instance.Round {
			time.Sleep(time.Millisecond * 20)
		}
		n, err = lb.hr.Get(key)
	}
	return n, err
}