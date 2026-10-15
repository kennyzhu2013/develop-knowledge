package avcache

import (
	"github.com/maypok86/otter/v2"
	"github.com/maypok86/otter/v2/stats"
)

type WTinyLfuCache[T AvItem] struct {
	cache *otter.Cache[string, T]
}

// NewWTinyLfuCache builds a W-TinyLFU cache bounded by total item bytes, the
// same limit semantics as SimpleCache/LruCache/LfuCache. A non-positive limit
// falls back to 1024 like the other implementations.
func NewWTinyLfuCache[T AvItem](limit int) *WTinyLfuCache[T] {
	if limit <= 0 {
		limit = 1024
	}
	cache := otter.Must(&otter.Options[string, T]{MaximumWeight: uint64(limit), StatsRecorder: &stats.NoopRecorder{},
		Weigher: func(_ string, val T) uint32 {
			size := uint64(val.Size())
			if size > uint64(^uint32(0)) {
				return ^uint32(0)
			}
			return uint32(size)
		},
	})
	return &WTinyLfuCache[T]{cache: cache}
}

func (c *WTinyLfuCache[T]) Put(val T) {
	c.cache.Set(val.Name(), val)
}

func (c *WTinyLfuCache[T]) Get(key string) (T, bool) {
	return c.cache.GetIfPresent(key)
}

func (c *WTinyLfuCache[T]) Del(key string) T {
	var t T
	if v, ok := c.cache.Invalidate(key); ok {
		t = v
	}
	return t
}

// Size returns the approximate total byte weight: otter updates the weighted
// size during its asynchronous maintenance, so the value is eventually
// consistent with the last Put/Get/Del.
func (c *WTinyLfuCache[T]) Size() int {
	return int(c.cache.WeightedSize())
}

func (c *WTinyLfuCache[T]) Elements() int {
	return c.cache.EstimatedSize()
}