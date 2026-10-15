package main

import (
	"os"
	"os/signal"
	"syscall"
	"vi-merge/internal/app"
	"vi-merge/internal/pkg/log"
)

func main() {

	//fmt.Println("vi-merge,main,start")

	//全局忽略信号，避免扫描异常
	signal.Ignore(syscall.SIGPIPE)

	//初始化服务
	app.ServerInit()

	//系统设置
	systemSetting()

	//启动服务
	app.StartService()

	app.Stop()

	//fmt.Println("vi-merge,main,end")

}

func systemSetting() {

	// 设置core
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
