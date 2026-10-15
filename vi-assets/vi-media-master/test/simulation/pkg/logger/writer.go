package logger

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

type Writer interface {
	io.Closer
	Write(*Event, bool) (int, error)
}

func Wrap(w io.Writer) (Writer, error) {
	if w == nil {
		return DiscardWriter{}, nil
	}
	if file, ok := w.(*os.File); ok {
		fw := &FileWriter{file: file, filename: file.Name(), cacheFiles: make([]string, 0, maxSegments)}
		if err := fw.initialize(); err != nil {
			return nil, err
		}
		return fw, nil
	}
	return nil, nil
}

type DiscardWriter struct{}

func (w DiscardWriter) Write(_ *Event, _ bool) (int, error) {
	return 0, nil
}

func (w DiscardWriter) Close() error {
	return nil
}

type GeneralWriter struct {
	w io.Writer
}

func (w *GeneralWriter) Write(event *Event, _ bool) (int, error) {
	return event.Msg(w.w)
}

func (w *GeneralWriter) Close() error {
	if w.w == nil {
		return nil
	}
	if cw, ok := w.w.(io.Closer); ok {
		return cw.Close()
	}
	return nil
}

type FileWriter struct {
	mu sync.Mutex

	pool *Pool

	filename   string
	cacheFiles []string

	file *os.File
	size int
}

func (w *FileWriter) initialize() error {
	items, err := os.ReadDir(filepath.Dir(w.filename))
	if err != nil {
		return fmt.Errorf("read the %s log directory failed: %v", filepath.Dir(w.filename), err)
	}
	fName := filepath.Base(w.filename)
	prefix := fName[:len(fName)-len(filepath.Ext(fName))]
	for _, item := range items {
		if item.IsDir() || !strings.HasPrefix(item.Name(), prefix) || len(item.Name()) <= len(fName) {
			continue
		}
		w.cacheFiles = append(w.cacheFiles, item.Name())
	}
	info, sErr := w.file.Stat()
	if info != nil {
		w.size = int(info.Size())
	}
	return sErr
}

func (w *FileWriter) Write(event *Event, force bool) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()

	n := event.Len()
	if n > segmentSize {
		return 0, ErrExceedMaxSegmentSize
	}
	if w.file == nil {
		if err := w.openOrNew(n); err != nil {
			return 0, err
		}
	}

	if !force && w.pool != nil {
		if err := w.pool.Put(event, writeTimeout); err == nil {
			return n, nil
		}
	}

	if w.size+n > segmentSize {
		if err := w.rotate(); err != nil {
			return 0, err
		}
	}
	nn, err := event.Msg(w.file)
	w.size += nn
	return nn, err
}

func (w *FileWriter) openOrNew(writeLen int) error {
	info, err := os.Stat(w.filename)
	if os.IsNotExist(err) {
		return w.openNew()
	}
	if err != nil {
		return fmt.Errorf("error getting log file info: %s", err)
	}

	if int(info.Size())+writeLen > segmentSize {
		return w.rotate()
	}

	file, err := os.OpenFile(w.filename, os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return w.openNew()
	}
	w.file = file
	w.size = int(info.Size())
	return nil
}

func (w *FileWriter) openNew() error {
	if err := os.MkdirAll(filepath.Dir(w.filename), 0755); err != nil {
		return fmt.Errorf("create directories failed: %v", err)
	}
	f, err := os.OpenFile(w.filename, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0666)
	if err != nil {
		return fmt.Errorf("create new log file failed: %v", err)
	}
	w.file = f
	w.size = 0
	return nil
}

func (w *FileWriter) rotate() error {
	if err := w.close(); err != nil {
		return err
	}
	// 删除最旧的日志文件
	dir := filepath.Dir(w.filename)
	if len(w.cacheFiles) == maxSegments {
		_ = os.Remove(filepath.Join(dir, w.cacheFiles[0]))
		w.cacheFiles = append(w.cacheFiles[:0], w.cacheFiles[1:]...)
	}
	// 重命名当前日志文件
	bName := backupName(w.filename)
	_ = os.Rename(w.filename, bName)
	w.cacheFiles = append(w.cacheFiles, bName)
	return w.openNew()
}

func (w *FileWriter) Async() {
	if w.pool == nil {
		w.pool = newPool(poolSize, bufferSize, HandlerFunc(func(data []byte) (int, error) {
			w.mu.Lock()
			defer w.mu.Unlock()
			if w.file == nil {
				return 0, nil
			}
			if w.size+len(data) > segmentSize {
				if err := w.rotate(); err != nil {
					return 0, err
				}
			}
			n, err := w.file.Write(data)
			w.size += n
			return n, err
		}))
	}
}

func (w *FileWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.pool != nil {
		_ = w.pool.Close()
		w.pool = nil
	}
	return w.close()
}

func (w *FileWriter) close() error {
	if w.file == nil {
		return nil
	}
	err := w.file.Close()
	w.file = nil
	return err
}

func backupName(name string) string {
	ext := filepath.Ext(name)
	prefix := name[:len(name)-len(ext)]
	timestamp := time.Now().Format("2006-01-02T15-04-05.000")
	return fmt.Sprintf("%s-%s%s", prefix, timestamp, ext)
}