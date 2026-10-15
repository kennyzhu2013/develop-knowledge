package cache

type SimpleCache[T any] struct {
	idx   int
	pos   int
	cache [][]T
}

func NewSimpleCache[T any](capacity int) *SimpleCache[T] {
	return &SimpleCache[T]{cache: make([][]T, 0, capacity)}
}

func (s *SimpleCache[T]) Add(item []T) {
	if item != nil {
		s.cache = append(s.cache, item)
	}
}

func (s *SimpleCache[T]) Get() (T, bool) {
	var t T
	if s.pos >= len(s.cache) || s.idx >= len(s.cache[s.pos]) {
		return t, false
	}
	t = s.cache[s.pos][s.idx]
	s.idx++
	if s.idx < len(s.cache[s.pos]) {
		return t, true
	}
	if s.pos+1 < len(s.cache) {
		if s.pos < (cap(s.cache) >> 1) {
			s.pos++
		} else {
			s.cache = append(s.cache[:0], s.cache[s.pos+1:]...)
			s.pos = 0
		}
	} else {
		s.cache = s.cache[:0]
		s.pos = 0
	}
	s.idx = 0
	return t, true
}

func (s *SimpleCache[T]) Reset() {
	s.cache = s.cache[:0]
	s.pos = 0
	s.idx = 0
}