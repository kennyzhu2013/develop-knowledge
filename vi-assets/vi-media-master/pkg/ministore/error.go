package ministore

import (
	"errors"
)

const (
	ErrFilePermission uint8 = iota + 1
	ErrFileNotExists
	ErrFileCreate
	ErrFileWrite
	ErrFileRead
	ErrFileMove

	ErrDirCreate

	ErrIndexNotExists
	ErrIndexDamage
	ErrIndexRename
	ErrIndexEmpty
	ErrIndexWrite
	ErrIndexRead
	ErrIndexEOF

	ErrConcurrentModification
	ErrInvalidLBPolicy
	ErrNotExists
	ErrExists
)

type CacheError struct {
	eType uint8
	msg   string
}

func (x *CacheError) Error() string {
	return x.msg
}

func Wrap(eType uint8, msg string) *CacheError {
	return &CacheError{eType: eType, msg: msg}
}

func IsFileCreate(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrFileCreate
	}
	return false
}

func IsFileWrite(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrFileWrite
	}
	return false
}

func IsFileRead(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrFileRead
	}
	return false
}

func IsFileMove(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrFileMove
	}
	return false
}

func IsFileNotExists(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrFileNotExists
	}
	return false
}

func IsFilePermission(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrFilePermission
	}
	return false
}

func IsDirCreate(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrDirCreate
	}
	return false
}

func IsIndexEOF(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrIndexEOF
	}
	return false
}

func IsIndexRead(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrIndexRead
	}
	return false
}

func IsIndexWrite(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrIndexWrite
	}
	return false
}

func IsIndexEmpty(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrIndexEmpty
	}
	return false
}

func IsIndexRename(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrIndexRename
	}
	return false
}

func IsIndexDamage(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrIndexDamage
	}
	return false
}

func IsIndexNotExists(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrIndexNotExists
	}
	return false
}

func IsExists(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrExists
	}
	return false
}

func IsNotExists(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrNotExists
	}
	return false
}

func IsInvalidLBPolicy(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrInvalidLBPolicy
	}
	return false
}

func IsConcurrentModification(err error) bool {
	var xErr *CacheError
	ok := errors.As(err, &xErr)
	if ok {
		return xErr.eType == ErrConcurrentModification
	}
	return false
}