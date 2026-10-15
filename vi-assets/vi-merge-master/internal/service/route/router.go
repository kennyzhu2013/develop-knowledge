package route

import (
	"vi-merge/internal/middleware"
	"vi-merge/internal/service/route/ops"
	"vi-merge/internal/service/route/wshandler"

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
	mediaGroup := Router.Group("/v1")
	{
		// 创建WebSocket连接处理器
		wsHandler := wshandler.NewConnHandler()
		mediaGroup.GET("/merge", wsHandler.AudioPcmWs)
		// mediaGroup.POST("/merge", wsHandler.AudioPcmWs)
	}

	// 监控、运维
	InnerRouter = gin.New()
	InnerRouter.Use(middleware.Recovery())
	InnerRouter.GET("/metrics", gin.WrapH(promhttp.Handler()))

	//InnerRouter.GET("/testmetrics", func(c *gin.Context) {
	//	metrics.CallSourceInc(models.MergeWav, strconv.Itoa(3))
	//	metrics.Send2BillFailInc(models.MergeWav)
	//	metrics.StreamMergeUploadTimeObserve(models.MergeWav, float64(30.0))
	//	c.JSON(200, gin.H{
	//		"status":  "ok",
	//		"message": "vi stream merge server is running",
	//	})
	//})

	omh := &ops.OMHandler{}
	omGroup := InnerRouter.Group("/ops")
	{
		// 配置操作
		// http://10.34.108.24:9034/ops/config/logLevel?value=debug
		// http://10.34.108.26:9034/ops/config/maxSession?value=700
		// http://10.34.108.26:9034/ops/configManifest

		omGroup.GET("/config/:config", omh.Config)
		omGroup.PUT("/config/:config", omh.Config)
		omGroup.GET("/configManifest", omh.ConfigManifest)
		// 服务操作
		omGroup.GET("/omu/:ops", omh.OMUServiceStatus)
	}

	//pprof is the third lib to register stack info.
	//the source code shows that we can set prefix.
	pprof.Register(InnerRouter)
}
