package ministore

import (
	"os"
	"time"
)

const (
	IsDirectory = iota
	IsRegular
	IsSymlink
)

type SysFile struct {
	FType int
	FName string
	//fLink  string
	FSize  int64
	FMtime time.Time
	fPerm  os.FileMode
}

type TreeInfos struct {
	Files []*SysFile
}

func (tree *TreeInfos) Visit(path string, f os.FileInfo, err error) error {
	if f == nil {
		return err
	}
	var tp int
	if f.IsDir() {
		tp = IsDirectory
	} else if (f.Mode() & os.ModeSymlink) > 0 {
		tp = IsSymlink
	} else {
		tp = IsRegular
	}
	inoFile := &SysFile{
		FName:  path,
		FType:  tp,
		fPerm:  f.Mode(),
		FMtime: f.ModTime(),
		FSize:  f.Size(),
	}
	tree.Files = append(tree.Files, inoFile)
	return nil
}

func PathExists(path string) (bool, error) {
	_, err := os.Stat(path)
	if err == nil {
		return true, nil
	}

	if os.IsExist(err) {
		return true, nil
	}

	return false, err
}