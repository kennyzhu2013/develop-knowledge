package avcache

import (
	"sync"
	"sync/atomic"
	"time"
)

type LruEntry[T AvItem] struct {
	// The prev next pointers
	prev, next *LruEntry[T]
	// K，V
	key string
	val T
	// The last accessed time
	accessedAt time.Time
}

type LruList[T AvItem] struct {
	root LruEntry[T]
	size atomic.Int64
}

func NewLruList[T AvItem]() *LruList[T] {
	l := new(LruList[T])
	l.root.prev = &l.root
	l.root.next = &l.root
	return l
}

func (l *LruList[T]) Put(key string, val T) *LruEntry[T] {
	entry := &LruEntry[T]{key: key, val: val, accessedAt: time.Now(), prev: &l.root, next: l.root.next}
	l.root.next.prev = entry
	l.root.next = entry
	l.size.Add(int64(val.Size()))
	return entry
}

func (l *LruList[T]) Remove() *LruEntry[T] {
	// The size counter can be zero while the list is non-empty (all entries
	// with zero bytes), so emptiness must be judged by the sentinel links.
	if l.root.next == &l.root {
		return nil
	}
	entry := l.root.prev
	entry.prev.next = entry.next
	entry.next.prev = entry.prev
	entry.next = nil
	entry.prev = nil
	l.size.Add(-int64(entry.val.Size()))
	return entry
}

func (l *LruList[T]) RemoveE(entry *LruEntry[T]) {
	if entry == nil {
		return
	}
	entry.prev.next = entry.next
	entry.next.prev = entry.prev
	entry.next = nil
	entry.prev = nil
	l.size.Add(-int64(entry.val.Size()))
}

func (l *LruList[T]) Move(entry *LruEntry[T]) {
	if entry == nil || entry.prev == nil || entry.next == nil {
		return
	}
	entry.prev.next = entry.next
	entry.next.prev = entry.prev
	entry.next = l.root.next
	entry.prev = &l.root
	l.root.next.prev = entry
	l.root.next = entry
}

func (l *LruList[T]) Replace(entry *LruEntry[T], val T) {
	l.size.Add(int64(val.Size() - entry.val.Size()))
	entry.val = val
}

func (l *LruList[T]) Size() int {
	return int(l.size.Load())
}

type LruCache[T AvItem] struct {
	mu sync.RWMutex
	// The maximum cache limit (bytes)
	limit int
	// The current element count
	elems atomic.Int64

	// LRU implementation
	// Utilizing a hash table combined with a doubly linked list
	list  *LruList[T]
	table map[string]*LruEntry[T]
}

func NewLruCache[T AvItem](limit int) *LruCache[T] {
	if limit <= 0 {
		limit = 1024
	}
	return &LruCache[T]{limit: limit, list: NewLruList[T](), table: map[string]*LruEntry[T]{}}
}

func (c *LruCache[T]) Put(val T) {
	c.mu.Lock()
	defer c.mu.Unlock()
	key := val.Name()
	if entry, ok := c.table[key]; ok {
		c.list.Move(entry)
		c.list.Replace(entry, val)
		for c.list.Size() > c.limit {
			oldestEntry := c.list.Remove()
			delete(c.table, oldestEntry.key)
			c.elems.Add(-1)
		}
		return
	}
	entry := c.list.Put(key, val)
	c.table[key] = entry
	c.elems.Add(1)
	for c.list.Size() > c.limit {
		oldestEntry := c.list.Remove()
		delete(c.table, oldestEntry.key)
		c.elems.Add(-1)
	}
}

func (c *LruCache[T]) Get(key string) (T, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	var t T
	if entry, ok := c.table[key]; ok {
		c.list.Move(entry)
		return entry.val, true
	}
	return t, false
}

func (c *LruCache[T]) Del(key string) T {
	c.mu.Lock()
	defer c.mu.Unlock()
	var t T
	if entry, ok := c.table[key]; ok {
		c.list.RemoveE(entry)
		delete(c.table, key)
		c.elems.Add(-1)
		return entry.val
	}
	return t
}

func (c *LruCache[T]) Size() int {
	if c.list == nil {
		return 0
	}
	return c.list.Size()
}

func (c *LruCache[T]) Elements() int {
	return int(c.elems.Load())
}