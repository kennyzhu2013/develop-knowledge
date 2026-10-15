package route

import (
	"offline-merge/internal/metrics"
	"offline-merge/internal/middleware"
	"offline-merge/internal/service/route/bizhandler"
	"offline-merge/internal/service/route/ops"

	"github.com/gin-contrib/pprof"
	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var Router *gin.Engine
var InnerRouter *gin.Engine

func Init() {
	gin.SetMode(gin.ReleaseMode)

	Router = gin.New()
	Router.Use(middleware.AccessLog())
	Router.Use(middleware.Recovery())

	//测试接口
	httpHandler := &bizhandler.HttpHandler{}
	mediaGroup := Router.Group("/v1")
	{
		mediaGroup.GET("/test", httpHandler.TestHandler)
		mediaGroup.POST("/test", httpHandler.TestHandler)
	}

	// 监控、运维
	InnerRouter = gin.New()
	InnerRouter.Use(middleware.Recovery())
	InnerRouter.GET("/metrics", gin.WrapH(promhttp.Handler()))
	InnerRouter.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{
			"status":  "ok",
			"message": "offline merge server is running",
		})
	})

	InnerRouter.GET("/testmetrics", func(c *gin.Context) {
		metrics.Send2BillFailInc("offline")
		metrics.MergeTimeObserve("dccMerge", float64(30.0))
		metrics.FfRunFailInc("offline")
		c.JSON(200, gin.H{
			"status":  "ok",
			"message": "vi stream merge server is running",
		})
	})

	omh := &ops.OMHandler{}
	omGroup := InnerRouter.Group("/ops")
	{
		// 配置操作
		// http://10.34.108.24:9037/ops/config/logLevel?value=debug  .:config as key to get real "logLevel"
		// http://10.34.108.24:9037/ops/config/uploadFailSaveLocal?value=1

		omGroup.GET("/config/:config", omh.Config)
		omGroup.PUT("/config/:config", omh.Config)

		omGroup.GET("/configManifest", omh.ConfigManifest)
		// 服务操作
		omGroup.GET("/omu/:ops", omh.OMUServiceStatus)

		// http://10.34.108.24:9037/ops/mq/stop
		omGroup.GET("/mq/:ops", omh.MqOpsStatus)
	}

	//pprof is the third lib to register stack info.
	//the source code shows that we can set prefix.
	pprof.Register(InnerRouter)
}
