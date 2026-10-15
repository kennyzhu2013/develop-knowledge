package internal

import (
	"os"
	"path/filepath"
	"simulation/pkg/logger"
)

var log *logger.Logger

func InitLog(path, name, level string, fields ...string) {
	if err := os.MkdirAll(path, 0755); err != nil {
		panic(err)
	}
	file, _ := os.OpenFile(filepath.Join(path, name), os.O_RDWR|os.O_CREATE|os.O_APPEND, 0666)
	l, err := logger.ParseLevel(level)
	if err != nil {
		panic(err)
	}
	log = logger.New(file).Level(l).Fields(fields...).Async()
}