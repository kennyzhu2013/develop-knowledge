package ministore

import (
	"io"
	"io/fs"
	"os"
	"path/filepath"
)

// IoStream 负责实际文件IO等操作（Disk或远程IO）
type IoStream interface {
	// Write 文件数据写入
	// 	dst：目标文件绝对路径
	Write(data []byte, dst string) error
	// Copy 文件拷贝
	// 	dst：目标文件绝对路径
	//	src：源文件绝对路径
	Copy(dst, src string) error
	// Move 文件移动
	// 	src：源文件绝对路径
	// 	dst：目标文件绝对路径
	Move(src, dst string) error
	// MkDir 创建目录
	MkDir(path string) error
	// Read 文件数据读取
	Read(path string) ([]byte, error)
	// Walk 目录扫描
	Walk(path string) (*TreeInfos, error)
	// Exists 文件目录是否存在
	Exists(path string) (bool, error)
	// Remove 文件目录删除
	Remove(path string) error
}

type DiskIoStream struct{}

func (d *DiskIoStream) Write(data []byte, dst string) error {
	// 多次创建同一个目录不会报错
	err := os.MkdirAll(filepath.Dir(dst), os.ModePerm)
	if err != nil {
		return Wrap(ErrDirCreate, err.Error())
	}
	fData, err := os.Create(dst)
	if err != nil {
		return Wrap(ErrFileCreate, err.Error())
	}
	defer func(fData *os.File) {
		_ = fData.Close()
	}(fData)
	_, err = fData.Write(data)
	if err != nil {
		// 写入失败，删除空文件
		_ = os.Remove(dst)
		return Wrap(ErrFileWrite, err.Error())
	}
	return nil
}

func (d *DiskIoStream) Copy(dst, src string) error {
	// 打开源文件
	srcReader, err := os.Open(src)
	if err != nil {
		return d.wrapReadErr(err)
	}
	defer func(srcReader *os.File) {
		_ = srcReader.Close()
	}(srcReader)
	// 创建目标文件
	err = os.MkdirAll(filepath.Dir(dst), os.ModePerm)
	if err != nil {
		return Wrap(ErrDirCreate, err.Error())
	}
	dstWriter, err := os.Create(dst)
	if err != nil {
		return Wrap(ErrFileCreate, err.Error())
	}
	defer func(fData *os.File) {
		_ = fData.Close()
	}(dstWriter)
	// 数据拷贝
	data, err := io.ReadAll(srcReader)
	if err != nil {
		return d.wrapReadErr(err)
	}
	_, err = dstWriter.Write(data)
	if err != nil {
		_ = os.Remove(dst)
		return Wrap(ErrFileWrite, err.Error())
	}
	return nil
}

func (d *DiskIoStream) Move(src, dst string) error {
	err := os.MkdirAll(filepath.Dir(dst), os.ModePerm)
	if err != nil {
		return Wrap(ErrDirCreate, err.Error())
	}
	err = os.Rename(src, dst)
	if err == nil {
		return nil
	}
	if os.IsNotExist(err) {
		return Wrap(ErrFileNotExists, err.Error())
	}
	if os.IsPermission(err) {
		return Wrap(ErrFilePermission, err.Error())
	}
	return Wrap(ErrFileMove, err.Error())
}

func (d *DiskIoStream) MkDir(path string) error {
	err := os.MkdirAll(path, os.ModePerm)
	if err != nil {
		return Wrap(ErrDirCreate, err.Error())
	}
	return nil
}

func (d *DiskIoStream) Read(path string) ([]byte, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, d.wrapReadErr(err)
	}
	return data, nil
}

func (d *DiskIoStream) Walk(dir string) (*TreeInfos, error) {
	var fileTree TreeInfos
	if err := filepath.Walk(dir, func(path string, info fs.FileInfo, err error) error {
		return fileTree.Visit(path, info, err)
	}); err != nil {
		return nil, err
	}
	return &fileTree, nil
}

func (d *DiskIoStream) Exists(path string) (bool, error) {
	return PathExists(path)
}

func (d *DiskIoStream) Remove(path string) error {
	return os.Remove(path)
}

func (d *DiskIoStream) wrapReadErr(err error) error {
	if os.IsNotExist(err) {
		return Wrap(ErrFileNotExists, err.Error())
	}
	if os.IsPermission(err) {
		return Wrap(ErrFilePermission, err.Error())
	}
	return Wrap(ErrFileRead, err.Error())
}