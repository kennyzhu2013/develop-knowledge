package ministore

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
)

// FileName db的默认文件名
const FileName = "miniDb.data"
const MergeFileName = "miniDb.data.merge"
const RebuildFileName = "miniDb.data.rebuild"

// IdxDBFile 索引数据库文件定义
type IdxDBFile struct {
	File *os.File
	// open时默认为db文件结尾处的长度
	Offset int64
}

// NewDBFile 新建一个索引数据文件
func NewDBFile(path string) (*IdxDBFile, error) {
	return NewDBFileWithFullName(filepath.Join(path, FileName), os.O_RDWR|os.O_CREATE)
}

// NewMergeDBFile 新建一个合并时的索引数据文件
func NewMergeDBFile(path string) (*IdxDBFile, error) {
	return NewDBFileWithFullName(filepath.Join(path, MergeFileName), os.O_RDWR|os.O_CREATE|os.O_TRUNC)
}

func NewDBFileWithFullName(fileName string, flag int) (*IdxDBFile, error) {
	file, err := os.OpenFile(fileName, flag, 0600)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, Wrap(ErrFileNotExists, err.Error())
		}
		if os.IsPermission(err) {
			return nil, Wrap(ErrFilePermission, err.Error())
		}
		return nil, Wrap(ErrFileRead, err.Error())
	}

	stat, err := os.Stat(fileName)
	if err != nil {
		return nil, Wrap(ErrFileRead, err.Error())
	}
	return &IdxDBFile{Offset: stat.Size(), File: file}, nil
}

func (df *IdxDBFile) Open(fileName string) error {
	file, err := os.OpenFile(fileName, os.O_RDWR|os.O_CREATE, 0600)
	if err != nil {
		if os.IsNotExist(err) {
			return Wrap(ErrFileNotExists, err.Error())
		}
		if os.IsPermission(err) {
			return Wrap(ErrFilePermission, err.Error())
		}
		return Wrap(ErrFileRead, err.Error())
	}
	stat, err := os.Stat(fileName)
	if err != nil {
		return Wrap(ErrFileRead, err.Error())
	}
	df.File = file
	df.Offset = stat.Size()
	return nil
}

// Read 从 offset 处开始读取entity：物理偏移量处读取
func (df *IdxDBFile) Read(offset int64) (*Entity, error) {
	buf := make([]byte, entityHeaderSize)
	if _, err := df.File.ReadAt(buf, offset); err != nil {
		return nil, df.wrapReadErr(err)
	}
	entity, err := Decode(buf)
	if err != nil {
		return nil, df.wrapReadErr(err)
	}

	offset += entityHeaderSize
	if entity.KeySize > 0 {
		key := make([]byte, entity.KeySize)
		if _, err = df.File.ReadAt(key, offset); err != nil {
			return nil, df.wrapReadErr(err)
		}
		entity.Key = key
	}

	offset += int64(entity.KeySize)
	if entity.ValueSize > 0 {
		value := make([]byte, entity.ValueSize)
		if _, err = df.File.ReadAt(value, offset); err != nil {
			return nil, df.wrapReadErr(err)
		}
		entity.Value = value
	}
	return entity, nil
}

// Write 当前Offset写入Entry
func (df *IdxDBFile) Write(e *Entity) error {
	enc, err := e.Encode()
	if err != nil {
		return Wrap(ErrIndexWrite, err.Error())
	}
	n, err := df.File.WriteAt(enc, df.Offset)
	if err != nil {
		return Wrap(ErrIndexWrite, fmt.Sprintf("%v: write %v from %v", err.Error(), n, df.Offset))
	}
	if n <= 0 {
		return Wrap(ErrIndexWrite, "no data")
	}
	df.Offset += int64(n)
	return nil
}

func (df *IdxDBFile) Close() error {
	if df.File == nil {
		return nil
	}
	return df.File.Close()
}

func (df *IdxDBFile) Name() string {
	if df.File == nil {
		return ""
	}
	return df.File.Name()
}

func (df *IdxDBFile) wrapReadErr(err error) error {
	if err == io.EOF {
		return Wrap(ErrIndexEOF, err.Error())
	}
	return Wrap(ErrIndexRead, err.Error())
}