package avcache

import (
	"sync"
	"sync/atomic"
)

type SimpleCache[T AvItem] struct {
	mu sync.RWMutex
	// The maximum cache limit (bytes)
	limit int
	// The current cache size (bytes)
	size atomic.Int64
	// The current element count
	elems atomic.Int64

	// Hash-indexed FIFO queue. Positions in [head, wIdx) record insertion
	// order: the oldest element sits at head and is the eviction target, so
	// newly added items survive until older ones are gone. Deleted slots
	// become holes (their index entry is removed) and are skipped during
	// eviction and reclaimed during compaction.
	index map[uint64]int
	slice []T
	head  int
	wIdx  int

	hash Hasher
}

func NewSimpleCache[T AvItem](limit int) *SimpleCache[T] {
	if limit <= 0 {
		limit = 1024
	}
	return &SimpleCache[T]{limit: limit, index: map[uint64]int{}, slice: make([]T, 0, 1024), hash: newDefaultHasher()}
}

func (c *SimpleCache[T]) Put(val T) {
	c.mu.Lock()
	defer c.mu.Unlock()
	hashKey := c.hash.Sum64(val.Name())
	if pos, ok := c.index[hashKey]; ok {
		// A hash hit only means an update when the names actually match.
		// On a collision with a different item, do not overwrite it (that
		// would corrupt the existing entry); the new value is simply left
		// uncached, which degrades to a miss rather than wrong content.
		if c.slice[pos].Name() == val.Name() {
			c.size.Add(int64(val.Size() - c.slice[pos].Size()))
			c.slice[pos] = val
			c.evict()
		}
		return
	}
	if c.wIdx == len(c.slice) {
		c.compactOrGrow()
	}
	c.slice[c.wIdx] = val
	c.index[hashKey] = c.wIdx
	c.size.Add(int64(val.Size()))
	c.elems.Add(1)
	c.wIdx++
	c.evict()
}

// evict removes the oldest live elements (from the head of the queue) until
// the total size is within the limit. Holes left by deleted items are skipped.
func (c *SimpleCache[T]) evict() {
	for c.size.Load() > int64(c.limit) && c.head < c.wIdx {
		t := c.slice[c.head]
		hashKey := c.hash.Sum64(t.Name())
		// A hole (stale slot) has no live index entry pointing back to it.
		if pos, ok := c.index[hashKey]; ok && pos == c.head {
			delete(c.index, hashKey)
			c.size.Add(-int64(t.Size()))
			c.elems.Add(-1)
		}
		var zero T
		c.slice[c.head] = zero
		c.head++
	}
}

// compactOrGrow makes room for one more element. Dead slots — the evicted
// [0, head) prefix plus interior holes left by Del — are reclaimed by
// rebuilding the window (stably shifting live elements to the front and
// remapping index positions) once the total dead count exceeds half the
// window; otherwise the backing slice is grown. The threshold keeps the
// rebuild amortized O(1) per operation and bounds the slice at a small
// constant multiple of the live element count.
func (c *SimpleCache[T]) compactOrGrow() {
	live := len(c.index)
	window := c.wIdx - c.head
	// Total dead slots = the prefix [0,head) left by eviction/head-Del plus
	// the interior holes: (wIdx-head)-live + head = wIdx-live. Counting only
	// the interior holes (window-live) would let the dead prefix grow
	// without ever triggering a rebuild, making the backing slice grow
	// unbounded under normal churn.
	dead := c.wIdx - live
	// Rebuild when dead slots dominate, or when everything is dead (an
	// empty window still needs its first slot back).
	if dead > window/2 || live == 0 {
		c.rebuild()
		return
	}
	c.slice = append(c.slice, make([]T, len(c.slice)+1)...)
}

// rebuild stably compacts the live window into [0, live), dropping the head
// prefix and interior holes and zeroing the vacated tail so deleted items
// are not pinned by stale references.
func (c *SimpleCache[T]) rebuild() {
	rev := make(map[int]uint64, len(c.index))
	for h, p := range c.index {
		rev[p] = h
	}
	w := 0
	for p := c.head; p < c.wIdx; p++ {
		if h, ok := rev[p]; ok {
			c.slice[w] = c.slice[p]
			c.index[h] = w
			w++
		}
	}
	var zero T
	for i := w; i < len(c.slice); i++ {
		c.slice[i] = zero
	}
	c.head = 0
	c.wIdx = w
	if len(c.slice) < w+1 {
		c.slice = append(c.slice, make([]T, w+1-len(c.slice))...)
	} else {
		c.slice = c.slice[:w+1]
	}
}

func (c *SimpleCache[T]) Get(key string) (T, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	var t T
	if pos, ok := c.index[c.hash.Sum64(key)]; ok {
		// Hash collisions are possible; verify the name so we never return a
		// different item that happens to share the hash.
		if c.slice[pos].Name() == key {
			return c.slice[pos], true
		}
	}
	return t, false
}

func (c *SimpleCache[T]) Del(key string) T {
	c.mu.Lock()
	defer c.mu.Unlock()
	var t T
	hashKey := c.hash.Sum64(key)
	pos, ok := c.index[hashKey]
	if !ok {
		return t
	}
	// Hash collision: the slot may belong to a different item. Do not delete
	// it; the requested key is simply not cached.
	if c.slice[pos].Name() != key {
		return t
	}
	t = c.slice[pos]
	delete(c.index, hashKey)
	c.size.Add(-int64(t.Size()))
	c.elems.Add(-1)
	if pos == c.head {
		// Removing the oldest element: zero the slot to release the
		// reference, then advance the head so eviction keeps targeting the
		// true oldest live element. (Interior holes are intentionally left
		// non-nil so evict() can still recognise them by re-hashing.)
		var zero T
		c.slice[pos] = zero
		c.head++
	}
	// Reclaim dead space eagerly: without this, a shrinking live set (or a
	// growing dead prefix from head deletions) keeps the window — and the
	// deleted items pinned inside it — until the next size-pressure event.
	// Count the dead prefix too, not just interior holes.
	if live := len(c.index); c.wIdx-live > (c.wIdx-c.head)/2 {
		c.rebuild()
	}
	return t
}

func (c *SimpleCache[T]) Size() int {
	return int(c.size.Load())
}

func (c *SimpleCache[T]) Elements() int {
	return int(c.elems.Load())
}