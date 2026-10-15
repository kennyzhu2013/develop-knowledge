package mem

import (
	"errors"
	"unsafe"
)

const (
	// O_DIRECT alignment is 512B
	blockSize = 512

	// Default buffer is 8KB (2 pages).
	defaultBufSize = 8192
)

// align returns an offset for alignment.
func align(b []byte) int {
	return int(uintptr(unsafe.Pointer(&b[0])) & uintptr(blockSize-1))
}

// allocAlignedBuf allocates buffer that is aligned by blockSize.
func allocAlignedBuf(n int) ([]byte, error) {
	if n == 0 {
		return nil, errors.New("size is `0` can't allocate buffer")
	}

	// Allocate memory buffer
	buf := make([]byte, n+blockSize)

	// First memmory alignment
	a1 := align(buf)
	offset := 0
	if a1 != 0 {
		offset = blockSize - a1
	}

	buf = buf[offset : offset+n]

	// Was alredy aligned. So just exit
	if a1 == 0 {
		return buf, nil
	}

	// Second alignment – check and exit
	a2 := align(buf)
	if a2 != 0 {
		return nil, errors.New("can't allocate aligned buffer")
	}

	return buf, nil
}
