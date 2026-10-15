package log

import (
	"cmic.chinamobile.com/ziyan/go-sdk/logger"
	"os"
	"path/filepath"
)

var Log *logger.Logger

func Init(path, name, level string, fields ...string) {
	if err := os.MkdirAll(path, 0755); err != nil {
		panic(err)
	}
	file, _ := os.OpenFile(filepath.Join(path, name), os.O_RDWR|os.O_CREATE|os.O_APPEND, 0666)
	l, err := logger.ParseLevel(level)
	if err != nil {
		panic(err)
	}
	Log = logger.New(file).Level(l).Fields(fields...).Async()
}

func DebugF(format string, args ...interface{}) {
	Log.DebugF(format, args...)
}

func InfoF(format string, args ...interface{}) {
	Log.InfoF(format, args...)
}

func WarnF(format string, args ...interface{}) {
	Log.WarnF(format, args...)
}

func ErrorF(format string, args ...interface{}) {
	Log.ErrorF(format, args...)
}

func FatalF(format string, args ...interface{}) {
	Log.FatalF(format, args...)
}

func DebugK(key string, format string, args ...interface{}) {
	Log.DebugK(key, format, args...)
}

func InfoK(key string, format string, args ...interface{}) {
	Log.InfoK(key, format, args...)
}

func WarnK(key string, format string, args ...interface{}) {
	Log.WarnK(key, format, args...)
}

func ErrorK(key string, format string, args ...interface{}) {
	Log.ErrorK(key, format, args...)
}

func FatalK(key string, format string, args ...interface{}) {
	Log.FatalK(key, format, args...)
}

func SysInfo(format string, args ...interface{}) {
	Log.SysInfo(format, args...)
}

func SysError(format string, args ...interface{}) {
	Log.SysError(format, args...)
}

func Level(level string) error {
	l, err := logger.ParseLevel(level)
	if err != nil {
		return err
	}
	Log.Level(l)
	return nil
}