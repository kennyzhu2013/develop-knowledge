package util

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const (
	BaseDir = "/data/offline/"
)

func FilepathJoin(root, path string) string {
	if root == "" {
		return ""
	}
	if !strings.HasPrefix(root, "/") {
		root = "/" + root
	}

	if path == "" {
		return root
	}

	if strings.HasPrefix(path, "/") || strings.HasSuffix(root, "/") {
		return root + path
	}

	return root + "/" + path
}

func CreateFileList(callId string, list []string) string {
	// 生成文件名：fileList_callId_时间戳.txt
	filename := fmt.Sprintf("fileList_%v_%d.txt", callId, time.Now().UnixMilli())
	audioRecordPath := FilepathJoin(getBaseDirPath(), filename) //filepath.Join(getBaseDirPath(), filename)
	//fmt.Printf("audioRecordPath=%v\n", audioRecordPath)

	// 构建内容
	var sb strings.Builder
	for _, p := range list {
		sb.WriteString(fmt.Sprintf("file '%v'\r\n", p))
	}

	ok := writeFile(audioRecordPath, sb.String())
	if ok {
		return audioRecordPath
	}
	return ""
}

func FileExists(path string) (bool, error) {
	info, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return false, nil
		}
		return false, err
	}
	// 存在且不是文件夹
	return !info.IsDir(), nil
}

func writeFile(filePath string, content string) bool {
	err := os.MkdirAll(filepath.Dir(filePath), 0755)
	if err != nil {
		return false
	}
	err = os.WriteFile(filePath, []byte(content), 0644)
	return err == nil
}

func getBaseDirPath() string {
	return BaseDir
}
