package middleware

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"github.com/gin-gonic/gin"
	"net/http"
	"vi-media/internal/dto"
	"vi-media/internal/pkg/log"
)

func Recovery() gin.HandlerFunc {
	return func(ctx *gin.Context) {
		defer func() {
			if err := recover(); err != nil {
				log.WarnF("Recovered from %v, cause: %v, stack: %v", ctx.Request.URL.Path, err, process.Stack())
				ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, "server error"))
			}
		}()
		ctx.Next()
	}
}