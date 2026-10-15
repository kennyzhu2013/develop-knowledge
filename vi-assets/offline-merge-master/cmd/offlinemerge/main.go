package main

import (
	"offline-merge/internal/app"
	"offline-merge/internal/pkg/log"
	"os"
	"syscall"
)

func main() {
	//fmt.Println("offline-merge,main,start")

	//初始化服务
	app.ServerInit()

	//系统设置
	systemSetting()

	//启动服务
	app.StartService()

	app.Stop()
	//fmt.Println("offline-merge,main,end")
}

func systemSetting() {

	//设置core
	rLimit := &syscall.Rlimit{Cur: 0x7FFFFFFFFF, Max: 0x7FFFFFFFFF}
	if err := syscall.Setrlimit(syscall.RLIMIT_CORE, rLimit); err != nil {
		log.SysError("Set rLimit_core failed: %v", err)
		os.Exit(-2)
	}
	// 设置openFiles
	rLimit.Cur = 0x7FFFF
	rLimit.Max = 0x7FFFF
	if err := syscall.Setrlimit(syscall.RLIMIT_NOFILE, rLimit); err != nil {
		log.SysError("Set rLimit_noFile failed: %v", err)
		os.Exit(-2)
	}

}
