package router

import (
	"github.com/gin-gonic/gin"
	"vi-media/internal/handler"
)

func InitProxyRouter(group *gin.RouterGroup) error {
	ph := &handler.ProxyHandler{}
	group.POST("/session/create", ph.SessionCreate)
	group.POST("/session/update", ph.Session, ph.SessionUpdate)
	group.POST("/session/pickup", ph.Session, ph.SessionPickup)
	group.POST("/session/stop", ph.Session, ph.SessionStop)
	group.POST("/media/dtmf", ph.Session, ph.MediaDtmf)
	group.POST("/media/play", ph.Session, ph.MediaPlay)
	group.POST("/media/inspect", ph.Session, ph.MediaInspect)
	group.POST("/media/inspect/notice", ph.Session, ph.MediaInspectNotice)
	group.POST("/media/stream", ph.Session, ph.MediaStream)
	group.POST("/media/stop", ph.Session, ph.MediaStop)
	return nil
}