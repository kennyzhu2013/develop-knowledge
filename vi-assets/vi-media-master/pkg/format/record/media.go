package record

import (
	"bytes"
	"errors"
	"os"
	"path/filepath"
)

type AVMedia interface {
	Write([]byte) ([]byte, int, int, error)
	Flush() ([]byte, error)
	Close() ([]byte, error)

	MimeType() string
	Volume() string
	Path() string
	Name() string

	MarkInMemory(bool)
	IsEarly() bool
	Mem() bool
}

type MediaInfo struct {
	file *os.File
	buf  *bytes.Buffer
	off  int

	name     string
	path     string
	volume   string
	mimeType string

	bMem   bool
	bEarly bool
	bStore bool
}

func (f *MediaInfo) Create(volume, path, name string) error {
	f.name = name
	f.path = path
	f.volume = volume
	f.bStore = true
	err := os.MkdirAll(filepath.Join(volume, path), os.ModePerm)
	if err != nil {
		return err
	}
	f.file, err = os.Create(f.FileName())
	return err
}

func (f *MediaInfo) Flush() ([]byte, error) {
	if f.buf == nil || f.buf.Len() == 0 {
		return nil, errors.New("empty buffer")
	}
	if !f.bMem {
		return nil, errors.New("invalid mode")
	}
	data := make([]byte, f.buf.Len())
	copy(data, f.buf.Bytes())
	return data, nil
}

func (f *MediaInfo) Persist() (int, error) {
	if f.buf == nil || f.buf.Len() == 0 {
		return 0, errors.New("empty buffer")
	}
	if f.file == nil {
		return 0, errors.New("file not initialized")
	}
	n, err := f.file.Write(f.buf.Bytes()[f.off:])
	if f.bMem {
		f.off += n
	} else {
		f.off = 0
		f.buf.Reset()
	}
	return n, err
}

func (f *MediaInfo) Write(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if f.buf == nil {
		return 0, errors.New("empty buffer")
	}
	if !f.bStore {
		return 0, nil
	}
	return f.buf.Write(p)
}

func (f *MediaInfo) Close() error {
	var err error
	if f.file != nil {
		err = f.file.Close()
		f.file = nil
	}
	if f.buf != nil {
		writeCache.Put(f.buf)
		f.buf = nil
	}
	return err
}

func (f *MediaInfo) WriteHeader(header []byte) (int, error) {
	return f.file.Write(header)
}

func (f *MediaInfo) SetHeader(header []byte) (int, error) {
	return f.file.WriteAt(header, 0)
}

func (f *MediaInfo) IsEnough(expect int) bool {
	return f.buf.Len() >= expect
}

func (f *MediaInfo) Mem() bool {
	return f.bMem
}

func (f *MediaInfo) IsEarly() bool {
	return f.bEarly
}

func (f *MediaInfo) IsDeleted() bool {
	return !f.bStore
}

func (f *MediaInfo) IsWriteable() bool {
	return f.file != nil
}

func (f *MediaInfo) MimeType() string {
	return f.mimeType
}

func (f *MediaInfo) FileName() string {
	return filepath.Join(f.volume, f.path, f.name)
}

func (f *MediaInfo) Volume() string {
	return f.volume
}

func (f *MediaInfo) Path() string {
	return f.path
}

func (f *MediaInfo) Name() string {
	return f.name
}

func (f *MediaInfo) MarkInMemory(bMem bool) {
	f.bMem = bMem
}

type BaseInfo struct {
	MediaInfo
	Packet
}

func (b *BaseInfo) Init(bMem, bEarly bool, mimeType, volume, path, name string) error {
	err := b.Create(volume, path, name)
	if err != nil {
		return err
	}
	b.mimeType = mimeType
	b.bMem = bMem
	b.bEarly = bEarly
	b.MediaInfo.buf = writeCache.Get().(*bytes.Buffer)
	b.MediaInfo.buf.Reset()
	return nil
}