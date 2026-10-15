package router

import (
	"github.com/gin-gonic/gin"
	"vi-media/internal/handler"
)

func InitSomRouter(group *gin.RouterGroup) error {
	sh := &handler.SomHandler{}
	group.GET("/config/:config", sh.Config)
	group.GET("/service/:ops", sh.ServiceStatus)
	group.GET("/cache/media/reload", sh.MediaCacheReload)
	group.GET("/cache/media/lookup", sh.MediaCacheLookup)
	group.GET("/cache/media/elements", sh.MediaCacheElements)
	group.GET("/cache/index/rewrite", sh.MediaCacheRewrite)
	return nil
}