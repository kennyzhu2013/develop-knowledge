package internal

import (
	"cmic.chinamobile.com/ziyan/go-sdk/metric"
	"cmic.chinamobile.com/ziyan/go-sdk/version"
	"context"
	"errors"
	"fmt"
	"github.com/gin-contrib/pprof"
	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"net/http"
	"os/signal"
	"runtime"
	"syscall"
	"vi-media/internal/conf"
	"vi-media/internal/middleware"
	"vi-media/internal/pkg/broker"
	"vi-media/internal/pkg/kv"
	"vi-media/internal/pkg/kv/registry"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/monitor"
	"vi-media/internal/pkg/rlimit"
	"vi-media/internal/proxy"
	"vi-media/internal/router"
	"vi-media/pkg/cmd"
)

const namespace = "vi-media"
const defaultConfigFile = "config/app.toml"

type App struct {
	ctx    context.Context
	cancel context.CancelFunc

	server *http.Server
}

func New(cfgFile string) (*App, error) {
	if len(cfgFile) == 0 {
		cfgFile = defaultConfigFile
	}
	if err := conf.Load(cfgFile); err != nil {
		return nil, err
	}
	// Logger
	log.Init(conf.App.Logger.Path, conf.App.Logger.File, conf.App.Logger.Level, conf.App.ApiAddress)
	log.SysInfo("Init Logger, path: %v, file: %v, level: %v", conf.App.Logger.Path, conf.App.Logger.File, conf.App.Logger.Level)
	return &App{}, nil
}

func (a *App) ServeAndListen() error {
	// Resource Reservation
	if err := rlimit.Reserve(); err != nil {
		log.SysError("RLimit reserve failed: %v", err)
		return err
	}
	if runtime.GOOS != "windows" {
		if out, err := cmd.Exec("cat", "/proc/sys/net/ipv4/ip_local_reserved_ports"); err != nil || len(out) < 9 {
			log.SysError("No config /proc/sys/net/ipv4/ip_local_reserved_ports: %v/%v", err, out)
			return fmt.Errorf("not config /proc/sys/net/ipv4/ip_local_reserved_ports: %v/%v", err, out)
		}
	}
	a.ctx, a.cancel = signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	metric.DefaultMonitor = metric.New(conf.App.ApiAddress, version.Version, namespace)
	// Messaging
	if err := broker.Init(conf.App.Messaging.Namespace, conf.App.Messaging.Topic, conf.App.Messaging.AccessKey, conf.App.Messaging.SecretKey,
		conf.App.Messaging.Endpoint, conf.App.Messaging.MaxAttempts); err != nil {
		log.SysError("Broker init failed: %v", err)
		return err
	}
	log.SysInfo("Init broker success")
	// KV
	if err := kv.Init(); err != nil {
		log.SysError("KV init failed: %v", err)
		return err
	}
	// RTP Proxy
	if err := proxy.Init(a.ctx, namespace); err != nil {
		log.SysError("Proxy init failed: %v", err)
		return err
	}
	// Register router
	gin.SetMode(gin.ReleaseMode)
	engine := gin.New()
	engine.Use(middleware.Recovery(), middleware.AccessLog())
	engine.GET("/metrics", gin.WrapH(promhttp.Handler()))
	if err := router.InitSomRouter(engine.Group("/som")); err != nil {
		log.SysError("SomRouter init failed: %v", err)
		return err
	}
	if err := router.InitMediaRouter(engine.Group("/media")); err != nil {
		log.SysError("MediaRouter init failed: %v", err)
		return err
	}
	if err := router.InitProxyRouter(engine.Group("/call")); err != nil {
		log.SysError("ProxyRouter init failed: %v", err)
		return err
	}
	pprof.Register(engine)
	// Start system service
	a.server = &http.Server{Handler: engine, Addr: conf.App.ApiAddress}
	go func() {
		var err error
		if conf.App.ServerCert != "" || conf.App.ServerKey != "" {
			err = a.server.ListenAndServeTLS(conf.App.ServerCert, conf.App.ServerKey)
		} else {
			err = a.server.ListenAndServe()
		}
		if err != nil {
			if errors.Is(err, http.ErrServerClosed) {
				return
			}
			panic(err)
		}
	}()
	log.SysInfo("Listen on %v success", conf.App.ApiAddress)
	// Register
	registry.Register(a.ctx)
	// Start monitoring
	if conf.App.Monitor.CPU.Enable {
		monitor.Run(a.ctx, func(state monitor.State) {
			if state == monitor.Overload {
				registry.TurnOverloaded()
			} else {
				registry.TurnUp()
			}
		})
	}
	<-a.ctx.Done()
	return nil
}

func (a *App) Shutdown() error {
	registry.Close()
	proxy.Stop()
	if a.server != nil {
		_ = a.server.Shutdown(context.Background())
	}
	if a.cancel != nil {
		a.cancel()
	}
	proxy.Wait()
	_ = broker.Stop()
	return nil
}