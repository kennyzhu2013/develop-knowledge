package process

import (
	"runtime"
)

const MaxStack = 1 << 12

func Stack() string {
	trace := make([]byte, MaxStack)
	n := runtime.Stack(trace, false)
	if n <= 0 {
		return ""
	}
	return string(trace[:n])
}
