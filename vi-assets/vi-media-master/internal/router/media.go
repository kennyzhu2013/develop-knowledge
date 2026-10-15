package router

import (
	"github.com/gin-gonic/gin"
	"vi-media/internal/handler"
)

func InitMediaRouter(group *gin.RouterGroup) error {
	mh := &handler.MediaHandler{}
	group.GET("/download/:filename", mh.Download)
	group.POST("/download/:filename", mh.Download)
	group.GET("/delete/:filename", mh.Delete)
	group.DELETE("/delete/:filename", mh.Delete)
	return nil
}