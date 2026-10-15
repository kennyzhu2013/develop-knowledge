package util

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

//func CreateDir(dir string) error {
//	if err := os.MkdirAll(dir, 0755); err != nil {
//		return err
//	}
//	return nil
//}

func IsDirExist(dirPath string) (bool, error) {
	// 先获取路径的文件信息
	fileInfo, err := os.Stat(dirPath)

	// 情况1：路径完全不存在
	if os.IsNotExist(err) {
		return false, nil
	}

	// 情况2：有其他错误（如权限不足、路径非法）
	if err != nil {
		return false, fmt.Errorf("check path fail: %w", err)
	}

	// 情况3：路径存在，但需要校验是否是目录（而非文件）
	if !fileInfo.IsDir() {
		return false, nil
	}

	// 情况4：路径存在且是目录
	return true, nil
}

func CreateDataDir(sessionId string, date string) (string, error) {
	var dir string
	if len(date) > 1 {
		dir = fmt.Sprintf("/data/merge/%v", date)
	} else {
		dir = "data/merge"
	}
	return CreateLocalDir(dir, sessionId)
}

func CreateLocalDir(root string, sessionId string) (string, error) {
	dir := fmt.Sprintf("%v/%v", root, sessionId)
	err := CreateDirIfNotExist(dir)
	return dir, err
}

func CreateDirIfNotExist(dirPath string) error {
	exist, err := IsDirExist(dirPath)
	if err != nil {
		return err
	}

	if !exist {
		// MkdirAll 支持创建多级目录（如 "a/b/c"），权限0755（所有者可读写执行，其他用户只读执行）
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

// 低IO写入替代原生WriteFile
func WriteFileLowIO(path string, data []byte, perm os.FileMode) error {
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, perm)
	if err != nil {
		return err
	}
	defer f.Close()

	// 开辟4MB缓冲，聚合写入
	w := bufio.NewWriterSize(f, 4<<20)
	if _, err := w.Write(data); err != nil {
		return err
	}
	// 刷缓冲到页缓存
	return w.Flush()
}

func RemoveDir(dir string) error {
	// 一次性读取当前目录所有子项，只一次readdir IO
	entries, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	// 先删所有文件
	for _, entry := range entries {
		fullPath := filepath.Join(dir, entry.Name())
		if entry.IsDir() {
			// 递归子目录
			if err = RemoveDir(fullPath); err != nil {
				return err
			}
		} else {
			if err = os.Remove(fullPath); err != nil {
				return err
			}
		}
	}
	return os.Remove(dir)
}

func FileLength(filePath string) (int64, error) {
	fileInfo, err := os.Stat(filePath)
	if err != nil {
		return 0, err
	}
	return fileInfo.Size(), nil
}

func FileExists(path string) (bool, error) {
	info, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return false, nil
		}
		return false, err
	}
	return !info.IsDir(), nil
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
