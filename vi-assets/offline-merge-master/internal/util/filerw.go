package util

import (
	"bufio"
	"fmt"
	"io"
	"offline-merge/internal/metrics"
	"offline-merge/internal/pkg/log"
	"os"
	"strings"
)

func IsDirExist(dirPath string) (bool, error) {
	fileInfo, err := os.Stat(dirPath)

	if os.IsNotExist(err) {
		return false, nil
	}

	if err != nil {
		return false, fmt.Errorf("check path fail: %w", err)
	}

	if !fileInfo.IsDir() {
		return false, nil
	}

	return true, nil
}

func CreateTmpDir(sessionId string) (string, error) {
	dir := fmt.Sprintf("%v/%v", "/tmp", sessionId)
	err := CreateDirIfNotExist(dir)
	return dir, err
}

func CreateDirIfNotExist(dirPath string) error {
	exist, err := IsDirExist(dirPath)
	if err != nil {
		return err
	}

	if !exist {
		err = os.MkdirAll(dirPath, os.ModePerm)
		if err != nil {
			return fmt.Errorf("MkdirAll,Fail: %w", err)
		}
	}
	return nil
}

func ReadLargeFileByBlock(filePath string, bufSize int) ([]byte, error) {
	f, err := os.OpenFile(filePath, os.O_RDONLY, 0644)
	if err != nil {
		return nil, fmt.Errorf("open file failed: %w", err)
	}
	defer f.Close()

	reader := bufio.NewReaderSize(f, bufSize)

	var totalBuf []byte
	block := make([]byte, bufSize)

	for {
		n, err := reader.Read(block)
		if n > 0 {
			// 追加当前块有效数据
			totalBuf = append(totalBuf, block[:n]...)
		}

		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, fmt.Errorf("read chunk failed: %w", err)
		}
	}

	return totalBuf, nil
}

const bufSize = 1 << 20 // 1MB 缓冲区

func CopyFileFast(srcPath, dstPath string) (int64, error) {
	src, err := os.Open(srcPath)
	if err != nil {
		return 0, err
	}
	defer src.Close()

	dst, err := os.OpenFile(dstPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
	if err != nil {
		return 0, err
	}
	defer dst.Close()

	// 包装缓冲读写
	r := bufio.NewReaderSize(src, bufSize)
	w := bufio.NewWriterSize(dst, bufSize)

	written, err := io.Copy(w, r)
	if err != nil {
		return written, err
	}

	// 刷新缓冲区到文件
	if err = w.Flush(); err != nil {
		return written, err
	}
	// 落盘
	err = dst.Sync()
	return written, err
}

func OpenFile(filePath string, appendMode bool) (*os.File, error) {
	var flag int
	if appendMode {
		flag = os.O_RDWR | os.O_CREATE | os.O_APPEND
	} else {
		flag = os.O_RDWR | os.O_CREATE | os.O_TRUNC
	}
	filePerm := os.FileMode(0644)

	file, err := os.OpenFile(filePath, flag, filePerm)
	if err != nil {
		return nil, fmt.Errorf("OpenFile Fail: %w", err)
	}
	return file, nil
}

func CreateDir(root, subDir, path string) string {
	baseRoot := FilepathJoin(root, subDir) //filepath.Join(root, subDir)
	basePath := ""

	if len(path) > 0 {
		basePath = FilepathJoin(baseRoot, path) //filepath.Join(baseRoot, path)
	} else {
		basePath = baseRoot
	}

	_, err := os.Stat(basePath)
	if os.IsNotExist(err) {
		// 创建多级目录，权限 0755
		err = os.MkdirAll(basePath, 0755)
		if err != nil {
			log.WarnK("1008811", "FileRw CreateDir/MkdirAll Fail,err:", err)
			metrics.FileRwErrInc("off-dir")
		}
	}

	return basePath
}

func FileLength(filePath string) (int64, error) {
	fileInfo, err := os.Stat(filePath)
	if err != nil {
		return 0, err
	}
	return fileInfo.Size(), nil
}

func IsFileExist(filePath string) bool {
	_, err := os.Stat(filePath)
	return !os.IsNotExist(err)
}

func ChangeExt(filename string, newExtension string) string {
	if filename == "" || newExtension == "" {
		return filename
	}

	lastIndex := strings.LastIndex(filename, ".")
	if lastIndex != -1 {
		return filename[:lastIndex] + "." + newExtension
	} else {
		return filename + "." + newExtension
	}
}
