package avcache

import (
	"sync"
	"sync/atomic"
)

type LfuEntry struct {
	// The prev next pointers
	prev, next *LfuEntry
	// The elements with the same frequency
	items map[uint64]struct{}
	// The count of the access
	freq uint
}

type LfuList struct {
	root LfuEntry
}

func NewLfuList() *LfuList {
	l := new(LfuList)
	// Initialize an empty entry as the bucket for the first data into the cache
	entry := &LfuEntry{prev: &l.root, next: &l.root, items: make(map[uint64]struct{})}
	l.root.prev = entry
	l.root.next = entry
	return l
}

func (l *LfuList) PutAfter(e *LfuEntry) *LfuEntry {
	entry := &LfuEntry{prev: e, next: e.next, items: make(map[uint64]struct{})}
	entry.next.prev = entry
	e.next = entry
	return entry
}

func (l *LfuList) Remove(e *LfuEntry) {
	e.prev.next = e.next
	e.next.prev = e.prev
	e.next = nil
	e.prev = nil
	e.items = nil
}

func (l *LfuList) Front() *LfuEntry {
	return l.root.next
}

type LfuItem[T AvItem] struct {
	t T
	// The frequency bucket to which the current element belongs
	bucket *LfuEntry
}

type LfuCache[T AvItem] struct {
	mu sync.RWMutex
	// The maximum cache limit (bytes)
	limit int
	// The current cache size (bytes)
	size atomic.Int64
	// The current element count
	elems atomic.Int64

	// Simple hashing combined with slicing implementation
	index map[uint64]int
	slice []*LfuItem[T]
	wIdx  int

	// LFU list implementation based on ascending frequency
	list *LfuList

	hash Hasher
}

func NewLfuCache[T AvItem](limit int) *LfuCache[T] {
	if limit <= 0 {
		limit = 1024
	}
	return &LfuCache[T]{limit: limit, index: map[uint64]int{}, slice: make([]*LfuItem[T], 0, 1024), hash: newDefaultHasher(), list: NewLfuList()}
}

func (c *LfuCache[T]) Put(val T) {
	c.mu.Lock()
	defer c.mu.Unlock()
	hashKey := c.hash.Sum64(val.Name())
	if idx, ok := c.index[hashKey]; ok {
		// A hash hit only means an update when the names actually match.
		// On a collision with a different item, do not overwrite it (that
		// would corrupt the existing entry); the new value is simply left
		// uncached, which degrades to a miss rather than wrong content.
		if c.slice[idx].t.Name() == val.Name() {
			c.size.Add(int64(val.Size() - c.slice[idx].t.Size()))
			c.slice[idx].t = val
			c.evict()
		}
		return
	}
	entry := c.list.Front()
	entry.items[hashKey] = struct{}{}
	c.slice = append(c.slice, &LfuItem[T]{t: val, bucket: entry})
	c.index[hashKey] = c.wIdx
	c.size.Add(int64(val.Size()))
	c.elems.Add(1)
	c.wIdx++
	// Evict after every add, mirroring SimpleCache: an item larger than the
	// limit alone is dropped immediately, keeping Size() <= limit at all times.
	c.evict()
}

func (c *LfuCache[T]) evict() {
	// Walk the bucket list from the lowest frequency up. The list is
	// circular around the root sentinel, so stop when the root is reached
	// (a plain nil check would loop forever on an empty front bucket).
	entry := c.list.Front()
	// The size gate mirrors SimpleCache: when already at or under the limit
	// eviction is a no-op, so an in-limit update never evicts an item.
	for c.size.Load() > int64(c.limit) && entry != nil && entry != &c.list.root {
		// Capture the successor before iterating: removing the bucket below
		// sets entry.next to nil.
		next := entry.next
		for h := range entry.items {
			idx := c.index[h]
			c.size.Add(-int64(c.slice[idx].t.Size()))
			c.elems.Add(-1)
			c.wIdx--
			if c.wIdx == 0 {
				// Last element removed; nothing left to swap.
				c.slice = c.slice[:c.wIdx]
				delete(c.index, h)
				delete(entry.items, h)
				if entry.freq != 0 && len(entry.items) == 0 {
					c.list.Remove(entry)
				}
				return
			}
			if idx < c.wIdx {
				c.slice[idx] = c.slice[c.wIdx]
				c.index[c.hash.Sum64(c.slice[idx].t.Name())] = idx
			}
			delete(c.index, h)
			delete(entry.items, h)
			if entry.freq != 0 && len(entry.items) == 0 {
				c.list.Remove(entry)
			}
			// Truncate so len(slice)==wIdx: keeps Elements() accurate and
			// releases references to evicted items for GC.
			c.slice = c.slice[:c.wIdx]
			if c.size.Load() <= int64(c.limit) {
				return
			}
		}
		entry = next
	}
}

func (c *LfuCache[T]) Get(key string) (T, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	var t T
	hashKey := c.hash.Sum64(key)
	idx, ok := c.index[hashKey]
	if !ok {
		return t, false
	}
	// Hash collisions are possible; verify the name so we never return a
	// different item that happens to share the hash.
	if c.slice[idx].t.Name() != key {
		return t, false
	}
	bucket := c.slice[idx].bucket
	nextFreq := bucket.freq + 1
	delete(bucket.items, hashKey)
	// Whether reuse the empty current bucket
	removable := bucket.freq != 0 && len(bucket.items) == 0
	// Move item into a valid bucket
	nextBucket := bucket.next
	if nextBucket == &c.list.root {
		// The root sentinel marks the end of the real bucket list.
		nextBucket = nil
	}
	switch {
	case nextBucket == nil || nextBucket.freq > nextFreq:
		if removable {
			nextBucket = bucket
		} else {
			nextBucket = c.list.PutAfter(bucket)
		}
	case nextBucket.freq == nextFreq:
		if removable {
			c.list.Remove(bucket)
		}
	default:
		// Defensive: a real bucket exists at a lower freq than expected
		// (should not happen for a well-ordered list). Create a fresh one.
		if removable {
			nextBucket = bucket
		} else {
			nextBucket = c.list.PutAfter(bucket)
		}
	}
	nextBucket.items[hashKey] = struct{}{}
	nextBucket.freq = nextFreq
	c.slice[idx].bucket = nextBucket
	return c.slice[idx].t, true
}

func (c *LfuCache[T]) Del(key string) T {
	c.mu.Lock()
	defer c.mu.Unlock()
	var t T
	hashKey := c.hash.Sum64(key)
	idx, ok := c.index[hashKey]
	if !ok {
		return t
	}
	// Hash collision: the slot may belong to a different item. Do not delete
	// it; the requested key is simply not cached.
	if c.slice[idx].t.Name() != key {
		return t
	}
	bucket := c.slice[idx].bucket
	delete(bucket.items, hashKey)
	if bucket.freq != 0 && len(bucket.items) == 0 {
		c.list.Remove(bucket)
	}
	t = c.slice[idx].t
	delete(c.index, hashKey)
	c.size.Add(-int64(t.Size()))
	c.elems.Add(-1)
	last := c.wIdx - 1
	if idx != last {
		moved := c.slice[last]
		c.slice[idx] = moved
		c.index[c.hash.Sum64(moved.t.Name())] = idx
	}
	c.wIdx = last
	c.slice = c.slice[:c.wIdx]
	return t
}

func (c *LfuCache[T]) Size() int {
	return int(c.size.Load())
}

func (c *LfuCache[T]) Elements() int {
	return int(c.elems.Load())
}