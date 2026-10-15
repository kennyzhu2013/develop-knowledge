package engine

import (
	"context"
	"math"
	"sync"
)

const abortIndex int8 = math.MaxInt8 / 2

type Context struct {
	context.Context
	cancel context.CancelFunc

	engine *Engine

	index int8
	chain HandlerChain
	keys  map[string]interface{}

	wg sync.WaitGroup
	sync.RWMutex
}

func (c *Context) Async(fn func()) {
	c.wg.Add(1)
	go func() {
		defer c.wg.Done()
		fn()
	}()
}

func (c *Context) Next() {
	c.index++
	for c.index < int8(len(c.chain)) {
		c.chain[c.index](c)
		c.index++
	}
	c.cancel()
	c.wg.Wait()
}

func (c *Context) Abort() {
	c.index = abortIndex
}

func (c *Context) Toggle() {
	c.index = -1
	c.keys = nil
	c.Context, c.cancel = context.WithCancel(c.engine.root)
}

func (c *Context) Reset() {
	c.index = -1
	c.Context, c.cancel = context.WithCancel(c.engine.root)
	c.chain = nil
	c.keys = nil
}

func (c *Context) Set(key string, val interface{}) {
	c.Lock()
	defer c.Unlock()
	if c.keys == nil {
		c.keys = make(map[string]interface{})
	}
	c.keys[key] = val
}

func (c *Context) Inc(key string, val int) (int, bool) {
	c.Lock()
	defer c.Unlock()
	if c.keys == nil {
		return 0, false
	}
	old, ok := c.keys[key].(int)
	if !ok {
		return 0, false
	}
	if old != val {
		return 0, false
	}
	c.keys[key] = old + 1
	return old + 1, true
}

func (c *Context) Get(key string) (val interface{}, exists bool) {
	c.RLock()
	defer c.RUnlock()
	val, exists = c.keys[key]
	return
}

func (c *Context) GetString(key string) (s string) {
	if val, ok := c.Get(key); ok && val != nil {
		s, _ = val.(string)
	}
	return
}

func (c *Context) CompareAndSwapString(key string, val, expect string) bool {
	c.Lock()
	defer c.Unlock()
	if c.keys == nil {
		return false
	}
	old, ok := c.keys[key].(string)
	if !ok {
		return false
	}
	if old != expect {
		return false
	}
	c.keys[key] = val
	return true
}

func (c *Context) GetInt(key string) (i int) {
	if val, ok := c.Get(key); ok && val != nil {
		i, _ = val.(int)
	}
	return
}