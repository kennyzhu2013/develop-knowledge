//go:build !windows

package rlimit

import (
	"syscall"
)

func Reserve() error {
	rLimit := &syscall.Rlimit{Cur: 0x7FFFFFFFFF, Max: 0x7FFFFFFFFF}
	if err := syscall.Setrlimit(syscall.RLIMIT_CORE, rLimit); err != nil {
		return err
	}
	rLimit.Cur = 0x7FFFF
	rLimit.Max = 0x7FFFF
	return syscall.Setrlimit(syscall.RLIMIT_NOFILE, rLimit)
}