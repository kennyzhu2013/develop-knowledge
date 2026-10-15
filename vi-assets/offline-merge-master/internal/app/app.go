package app

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"offline-merge/conf"
	"offline-merge/internal/metrics"
	"offline-merge/internal/pkg/broker"
	"offline-merge/internal/pkg/ff"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/pkg/registry"
	"offline-merge/internal/service/route"
	"offline-merge/internal/util"
	"os"
	"os/signal"
	"syscall"
)

var ctx context.Context
var cancel context.CancelFunc
var ch chan os.Signal
var server *http.Server

func ServerInit() {
	if err := conf.Init("config/app.yaml"); err != nil {
		log.SysError("conf init failed: %v", err)
		panic(err.Error())
	}

	//日志
	log.Init(conf.App.LogPath, conf.App.LogFile, conf.App.LogLevel, conf.App.HttpAddress)
	log.SysInfo("Init Logger, path: %v, file: %v, level: %v", conf.App.LogPath, conf.App.LogFile, conf.App.LogLevel)

	//RocketMQ初始化
	if err := broker.Init(conf.App.RocketMsg.Namespace, conf.App.RocketMsg.ViTopic, conf.App.RocketMsg.DccTopic, conf.App.RocketMsg.AccessKey,
		conf.App.RocketMsg.SecretKey, conf.App.RocketMsg.Endpoint, conf.App.RocketMsg.MaxAttempts, conf.App.RocketMsg.ModeBiz); err != nil {
		log.SysError("Broker init failed: %v", err)
		panic(err.Error())
	}

	broker.StartConsumer()
	log.SysInfo("Init broker success")

	//ETCD
	ctx, cancel = context.WithCancel(context.Background())
	registry.Init(ctx)

	metrics.Init()

	// 初始化路由
	route.Init()

	//fileOk, _ := util.FileExists(conf.App.Ffmpeg.StaticPath)
	//if !fileOk {
	//	panic("ffmpeg path not exist")
	//}
	// 测试
	// ff.SetFFPath(conf.App.Ffmpeg.StaticPath)

	ffmpegInitCheck()
	log.SysInfo("Init end")
}

func ffmpegInitCheck() {
	wd, err := os.Getwd()
	if err != nil {
		log.SysInfo("Get work directory failed: %v", err)
	}
	ffPath := fmt.Sprintf("%v/ffmpeg", wd) //conf.App.Ffmpeg.StaticPath
	log.SysInfo("ffPath=%v", ffPath)
	fileOk, _ := util.FileExists(ffPath)

	if !fileOk {
		// 第一次查找不到则切换成配置目录查找
		ffPath = conf.App.Ffmpeg.StaticPath
		fileOk, _ = util.FileExists(ffPath)
		log.SysInfo("second,ffPath=%v", ffPath)
	}

	if !fileOk {
		log.SysInfo("ffmpeg path not exist")
		panic("ffmpeg path not exist")
	}

	ok, err := util.CanExecute(ffPath)
	if !ok {
		log.SysInfo("Can't execute ffmpeg,err=%v", err)
		panic("Can't execute ffmpeg")
	}

	ff.SetFFPath(ffPath)
}

func StartService() {
	//处理SIGPIPE异常
	watchSignal()

	// 启动运维监控
	innerHttpAddress := conf.App.InnerHttpAddress
	httpAddress := conf.App.HttpAddress
	go func() {
		err := route.InnerRouter.Run(innerHttpAddress)
		if err != nil {
			log.SysError("Service start failed: listen %v failed on %v", conf.App.InnerHttpAddress, err)
			os.Exit(-3)
		}
	}()

	// 启动业务路由
	ctx, cancel = signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	server = &http.Server{
		Handler: route.Router,
		Addr:    httpAddress,
	}
	go func() {
		err := server.ListenAndServe()
		if err != nil {
			if errors.Is(err, http.ErrServerClosed) {
				return
			}
			panic(err)
		}
	}()
	log.SysInfo("Listen on %v success", conf.App.HttpAddress)
	<-ctx.Done()
	cancel()
}

func watchSignal() {
	log.SysInfo("watchSignal start")

	ch = make(chan os.Signal, 1)
	signal.Notify(ch, syscall.SIGTERM, syscall.SIGINT, syscall.SIGKILL, syscall.SIGPIPE)
	go func() {
		defer func() {
			if v := recover(); v != nil {
				log.InfoF("watchSignal recover,v=%v", v)
			}
		}()

		for {
			select {
			case sig, ok := <-ch:
				if !ok {
					log.InfoF("[signal close return] ")
					return
				}
				log.InfoF("[Received signal] %v", sig)
				if sig == syscall.SIGPIPE {
					continue
				}
			}
		}
	}()

	log.SysInfo("watchSignal end")
}
func Stop() {
	log.SysInfo("main service Stop")

	if cancel != nil {
		cancel()
	}

	if ch != nil {
		close(ch)
	}

	//etcd释放
	registry.Close()

	//Rocket发送暂时
	_ = broker.StopProducer()

	broker.MqGracefulStop()

	// 关闭服务 	关闭会话
	log.SysInfo("main service stops...")
	if server != nil {
		server.Shutdown(context.Background())
	}

	log.SysInfo("main service stop end")

}
