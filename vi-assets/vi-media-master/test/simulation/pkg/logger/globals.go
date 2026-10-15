package logger

import (
	"errors"
	"strconv"
	"time"
)

type Level int8

const (
	DebugLevel Level = iota
	InfoLevel
	WarnLevel
	ErrorLevel
	FatalLevel
	SysInfoLevel
	SysErrorLevel
)

var (
	ErrEnqueueTimeout       = errors.New("enqueue timeout")
	ErrUnknownLevel         = errors.New("unknown level")
	ErrExceedMaxSegmentSize = errors.New("exceed max segment size")
	ErrExit                 = errors.New("exit")
)

var (
	// poolSize 缓冲池大小
	// 开启异步日志时生效，异步日志事件的缓冲个数，默认150个
	poolSize = 150

	// bufferSize 刷新缓冲区大小
	// 开启异步日志时生效，日志消息缓冲大小，默认2MB
	bufferSize = 1024 * 1024 * 2

	// writeTimeout 写入缓冲池的超时时间，默认20ms
	writeTimeout = 20 * time.Millisecond

	// flushInterval 刷新缓冲区间隔，默认5s
	flushInterval = 5000 * time.Millisecond

	// segmentSize 日志分片大小，默认300MB
	segmentSize = 1024 * 1024 * 300

	// maxSegments 日志最大分片数，默认50个
	maxSegments = 50
)

type Config struct {
	PoolSize      int
	BufferSize    int
	WriteTimeout  int
	FlushInterval int
	SegmentSize   int
	MaxSegments   int
}

func (l Level) String() string {
	switch l {
	case DebugLevel:
		return "debug"
	case InfoLevel:
		return "info"
	case WarnLevel:
		return "warn"
	case ErrorLevel:
		return "error"
	case FatalLevel:
		return "fatal"
	case SysInfoLevel:
		return "sysInfo"
	case SysErrorLevel:
		return "sysError"
	}
	return strconv.Itoa(int(l))
}

func ParseLevel(levelStr string) (Level, error) {
	switch levelStr {
	case "debug":
		return DebugLevel, nil
	case "info":
		return InfoLevel, nil
	case "warn":
		return WarnLevel, nil
	case "error":
		return ErrorLevel, nil
	case "fatal":
		return FatalLevel, nil
	case "sysInfo":
		return SysInfoLevel, nil
	case "sysError":
		return SysErrorLevel, nil
	}
	i, err := strconv.Atoi(levelStr)
	if err != nil || i > 127 || i < -128 {
		return Level(-1), ErrUnknownLevel
	}
	return InfoLevel, nil
}

func SetGlobalConfig(cfg Config) {
	if cfg.PoolSize > 0 {
		poolSize = cfg.PoolSize
	}
	if cfg.WriteTimeout > 0 {
		writeTimeout = time.Duration(cfg.WriteTimeout) * time.Millisecond
	}
	if cfg.FlushInterval > 0 {
		flushInterval = time.Duration(cfg.FlushInterval) * time.Millisecond
	}
	if cfg.MaxSegments > 0 {
		maxSegments = cfg.MaxSegments
	}
	if cfg.BufferSize > 0 {
		bufferSize = cfg.BufferSize
	}
	if cfg.SegmentSize > 0 {
		segmentSize = cfg.SegmentSize
	}
	if segmentSize < bufferSize {
		panic("SegmentSize must be greater than BufferSize")
	}
}