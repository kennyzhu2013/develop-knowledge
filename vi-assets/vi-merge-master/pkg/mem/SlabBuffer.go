package mem

import (
	"errors"
	"io"
	"sync"
)

// smallBufferSize is an initial allocation minimal capacity.
const smallBufferSize = 409600 // for one slice, must 512 aligns
const maxInt = int(^uint(0) >> 1)

// The zero value for Buffer is an empty buffer ready to use.
type SlabBuffer struct {
	buf []byte // contents are the bytes buf[off : len(buf)]
	off int    // read at &buf[off]
	on  int    // write at &buf[on]
	sync.Mutex
	align bool // 0 = not aliang.
}

// ErrTooLarge is passed to panic if memory cannot be allocated to store data in a buffer.
var ErrTooLarge = errors.New("bytes.Buffer: too large")
var errNegativeRead = errors.New("bytes.Buffer: reader returned negative count from Read")

func NewSlabBuff() *SlabBuffer {
	return &SlabBuffer{buf: make([]byte, smallBufferSize), off: 0, on: 0, align: false}
}

func NewSlabBuffInit(iSize int) *SlabBuffer {
	return &SlabBuffer{buf: make([]byte, iSize), off: 0, on: 0, align: false}
}

func NewSlabBuffInitAlign(iSize int) (slab *SlabBuffer, err error) {
	slab = &SlabBuffer{off: 0, on: 0, align: true}
	iSize = slab.alignSize(iSize)
	slab.buf, err = allocAlignedBuf(iSize)
	return
}

// return all reserved.
func (s *SlabBuffer) BytesUnread() []byte    { return s.buf[s.off:s.on] }
func (s *SlabBuffer) BytesUnreadLength() int { return s.on - s.off }

// empty reports whether the unread portion of the buffer is empty.
func (s *SlabBuffer) Empty() bool { return s.on <= s.off || s.buf == nil }

// Len returns the number of bytes of the unread portion of the buffer;
// b.Len() == len(b.Bytes()).
func (s *SlabBuffer) Len() int { return len(s.buf) - s.off }

func (s *SlabBuffer) BytesWritten() []byte { return s.buf[:s.on] }

func (s *SlabBuffer) BytesWrittenLen() int { return s.on }

// Cap returns the capacity of the buffer's underlying byte slice, that is, the
// total space allocated for the buffer's data.
func (s *SlabBuffer) Cap() int { return cap(s.buf) }

func (s *SlabBuffer) isFull() bool { return s.on >= len(s.buf)-1 }

func (s *SlabBuffer) isEnough(increment int) bool { return s.on+increment <= len(s.buf)-1 }

// Reset resets the buffer to be empty,
// but it retains the underlying storage for use by future writes.
func (s *SlabBuffer) Reset() {
	s.Lock()
	s.reset()
	s.Unlock()
}

func (s *SlabBuffer) alignSize(iSize int) int {
	if iSize%blockSize != 0 {
		// align to blockSize
		iSize = iSize & -blockSize
	}

	if iSize < defaultBufSize {
		iSize = defaultBufSize
	}
	return iSize
}

func (s *SlabBuffer) reset() {
	s.buf = s.buf[:0]
	s.off = 0
	s.on = 0
}

func (s *SlabBuffer) ResetOffOnUnSafe() {
	s.off = 0
	s.on = 0
}

func (s *SlabBuffer) tryGrowByReSlice(n int) (int, bool) {
	if l := len(s.buf); n <= cap(s.buf)-l {
		s.buf = s.buf[:l+n]
		return l, true
	}
	return 0, false
}

// If the buffer can't grow it will panic with ErrTooLarge.
func (s *SlabBuffer) grow(n int) int {
	m := s.Len()

	// If buffer is empty, reset to recover space.
	if m == 0 && s.off != 0 {
		s.reset()
	}

	// Try to grow by means of a reslice.
	if i, ok := s.tryGrowByReSlice(n); ok {
		return i
	}

	if s.buf == nil && n <= smallBufferSize {
		if s.align {
			s.buf, _ = allocAlignedBuf(s.alignSize(n))
		} else {
			s.buf = make([]byte, n, smallBufferSize)
		}
		return 0
	}

	c := cap(s.buf)
	if n <= c/2-m {
		// We can slide things down instead of allocating a new
		// slice. We only need m+n <= c to slide, but
		// we instead let capacity get twice as large so we
		// don't spend all our time copying.
		copy(s.buf, s.buf[s.off:])
	} else if c > maxInt-c-n {
		// impossible error
		panic(ErrTooLarge)
	} else {
		var buf []byte
		// Not enough space anywhere, we need to allocate.
		if s.align {
			buf, _ = allocAlignedBuf(s.alignSize(2*c + n))
		} else {
			buf = make([]byte, 2*c+n)
		}
		copy(buf, s.buf[s.off:])
		s.buf = buf
	}
	// Restore b.off and len(b.buf).
	s.on = s.on - s.off
	if s.on < 0 {
		s.on = 0
	}
	s.off = 0
	s.buf = s.buf[:m+n]
	return m
}

// write
func (s *SlabBuffer) Write(p []byte) (n int, err error) {
	s.Lock()
	defer s.Unlock()
	return s.WriteUnsafeDirectly(p)
}

func (s *SlabBuffer) WriteUnsafeDirectly(p []byte) (n int, err error) {
	if !s.isEnough(len(p)) {
		_, ok := s.tryGrowByReSlice(len(p))
		if !ok {
			_ = s.grow(len(p))
		}
	}

	count := copy(s.buf[s.on:], p)
	s.on += count
	return count, nil
}

func (s *SlabBuffer) WriteString(st string) (n int, err error) {
	return s.Write([]byte(st))
}

// read to bytes.
func (s *SlabBuffer) Read(p []byte) (n int, err error) {
	s.Lock()
	defer s.Unlock()
	if s.Empty() {
		// Buffer is empty, reset to recover space.
		s.reset()
		if len(p) == 0 {
			return 0, nil
		}
		return 0, io.EOF
	}

	bufLen := len(p)
	m := s.on - s.off
	if m <= 0 {
		return 0, io.EOF
	} else if m < bufLen {
		bufLen = m
	}
	if bufLen <= 0 {
		//log.Errorf("SlabBuffer Read error. bufLen:%v", bufLen)
		return 0, io.EOF
	}

	n = copy(p, s.buf[s.off:s.off+bufLen])
	s.off += bufLen
	return n, nil
}

func (s *SlabBuffer) CopyAll() ([]byte, error) {
	s.Lock()
	defer s.Unlock()

	if s.Empty() {
		s.reset()
		return nil, io.EOF
	}

	bufLen := s.Len()
	c := make([]byte, bufLen)
	copy(c, s.buf[s.off:s.off+bufLen])
	return c, nil
}

// read n bytes.
func (s *SlabBuffer) Next(n int) []byte {
	s.Lock()
	defer s.Unlock()
	m := s.on - s.off
	if n > m {
		n = m
	}

	data := s.buf[s.off : s.off+n]
	s.off += n
	return data
}
