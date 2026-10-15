package avcache

import "sync"

type TagCache struct {
	mu   sync.RWMutex
	keys map[string]struct{}
}

func NewTagCache() *TagCache {
	return &TagCache{keys: map[string]struct{}{}}
}

func (c *TagCache) Put(key string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.keys[key] = struct{}{}
}

func (c *TagCache) Del(key string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	delete(c.keys, key)
}

func (c *TagCache) Exists(key string) bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	_, ok := c.keys[key]
	return ok
}

func (c *TagCache) Keys() []string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	keys := make([]string, 0, len(c.keys))
	for k := range c.keys {
		keys = append(keys, k)
	}
	return keys
}

func (c *TagCache) Clear() {
	c.mu.Lock()
	defer c.mu.Unlock()
	for k := range c.keys {
		delete(c.keys, k)
	}
}