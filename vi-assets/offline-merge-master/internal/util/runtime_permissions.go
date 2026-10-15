package util

import (
	"errors"
	"fmt"
	"os"
)

// CanExecute 判断当前用户是否可以执行该文件
func CanExecute(path string) (bool, error) {
	stat, err := os.Stat(path)
	if err != nil {
		return false, errors.New("file not exist")
	}
	perm := stat.Mode().Perm()
	if perm == 0o755 {

		return true, nil
	}

	if perm == 0o777 {
		return true, nil
	}
	return false, fmt.Errorf("permission denied,perm=%v", perm)
}
