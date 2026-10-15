package avcache

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"
	"vi-media/pkg/rtp/codec"
)

type AvItem interface {
	Name() string
	MimeType() string
	ModTime() time.Time
	Size() int
}

type AudioItem struct {
	name    string
	mime    string
	modTime time.Time

	raw *codec.Fragment
	oct *codec.Fragment
}

func NewAudioItem(name, mimeType string, raw *codec.Fragment) *AudioItem {
	return &AudioItem{name, mimeType, time.Now(), raw, nil}
}

func (i *AudioItem) Get(mimeType string) *codec.Fragment {
	if mimeType == codec.MimeTypeAmrNBOctet || mimeType == codec.MimeTypeAmrWBOctet {
		return i.oct
	}
	return i.raw
}

func (i *AudioItem) SetOct(oct *codec.Fragment) {
	i.oct = oct
}

func (i *AudioItem) Name() string {
	return i.name
}

func (i *AudioItem) MimeType() string {
	return i.mime
}

func (i *AudioItem) ModTime() time.Time {
	return i.modTime
}

func (i *AudioItem) Size() int {
	size := 0
	if i.raw != nil {
		for idx := range i.raw.Frames {
			size += len(i.raw.Frames[idx])
		}
	}
	if i.oct != nil {
		for idx := range i.oct.Frames {
			size += len(i.oct.Frames[idx])
		}
	}
	return size
}

type VideoItem struct {
	name    string
	mime    string
	modTime time.Time

	raw []*codec.Fragment
}

func NewVideoItem(name, mimeType string, raw []*codec.Fragment) *VideoItem {
	return &VideoItem{name, mimeType, time.Now(), raw}
}

func (i *VideoItem) Get() []*codec.Fragment {
	return i.raw
}

func (i *VideoItem) Name() string {
	return i.name
}

func (i *VideoItem) MimeType() string {
	return i.mime
}

func (i *VideoItem) ModTime() time.Time {
	return i.modTime
}

func (i *VideoItem) Size() int {
	size := 0
	if i.raw != nil {
		for _, fg := range i.raw {
			for idx := range fg.Frames {
				size += len(fg.Frames[idx])
			}
		}
	}
	return size
}

type Cache[T AvItem] interface {
	Put(T)
	Get(string) (T, bool)
	Del(string) T
	Size() int
	Elements() int
}

type Loader[T AvItem] func(context.Context, string, string) (T, error)

type Manager[T AvItem] struct {
	root  string
	cache Cache[T]

	// mu serializes the post-loader cache/tag mutations in Load, so the
	// outcome of concurrent Loads for the same name is independent of
	// completion order: a late-arriving "not exists" failure can never
	// leave a name negatively tagged while the item is cached.
	mu sync.Mutex

	// A tag cache marking non-existent media
	tagCache *TagCache

	// Media resource loader for loading new media
	loader Loader[T]
}

func New[T AvItem](root string, cache Cache[T], loader Loader[T]) *Manager[T] {
	// A nil cache degrades to a default SimpleCache and a nil loader to a
	// miss-returning stub, so a partially initialized Manager never panics
	// on the first Get/Load.
	if cache == nil {
		cache = NewSimpleCache[T](1024)
	}
	if loader == nil {
		loader = func(ctx context.Context, mimeType, name string) (T, error) {
			var t T
			return t, ErrNotExists
		}
	}
	return &Manager[T]{root: root, cache: cache, tagCache: NewTagCache(), loader: loader}
}

func (m *Manager[T]) Get(ctx context.Context, mimeType, name string) (T, string, error) {
	if item, ok := m.cache.Get(name); ok {
		return item, item.MimeType(), nil
	}
	if m.tagCache.Exists(name) {
		var t T
		return t, mimeType, fmt.Errorf("%w: %v not exists", ErrNotExists, name)
	}
	return m.Load(ctx, mimeType, name)
}

func (m *Manager[T]) Load(ctx context.Context, mimeType, name string) (T, string, error) {
	item, err := m.loader(ctx, mimeType, name)
	// The loader runs outside the lock; only the result commit is
	// serialized, so expensive loads still proceed concurrently.
	m.mu.Lock()
	defer m.mu.Unlock()
	if err != nil {
		if errors.Is(err, ErrNotExists) {
			// A concurrent success may have cached the item while this
			// load was in flight. Tagging the name now would block
			// reloads forever once the item is evicted, so clear the
			// tag instead of marking the name negative.
			if _, ok := m.cache.Get(name); ok {
				m.tagCache.Del(name)
			} else {
				m.tagCache.Put(name)
			}
		}
		return item, mimeType, err
	}
	m.cache.Put(item)
	m.tagCache.Del(name)
	return item, item.MimeType(), nil
}

func (m *Manager[T]) Remove(name string) {
	_ = m.cache.Del(name)
	// Clear the negative-cache tag too: without this, a name previously
	// marked "not exists" stays short-circuited forever and Remove (the
	// only explicit refresh entry point) could never trigger a reload.
	m.tagCache.Del(name)
}

func (m *Manager[T]) Size() int {
	if m.cache != nil {
		return m.cache.Size()
	}
	return 0
}

func (m *Manager[T]) Elements() int {
	if m.cache != nil {
		return m.cache.Elements()
	}
	return 0
}

func (m *Manager[T]) Exists(name string) string {
	if _, ok := m.cache.Get(name); ok {
		return "exists"
	}
	if m.tagCache.Exists(name) {
		return "not exists"
	}
	return "not loaded"
}