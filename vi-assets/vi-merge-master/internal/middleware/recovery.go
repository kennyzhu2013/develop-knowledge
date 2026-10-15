package middleware

import (
	"github.com/gin-gonic/gin"
	"net/http"
	"runtime"
	"vi-merge/internal/pkg/log"
)

const MaxStack = 1 << 12

func Stack() string {
	trace := make([]byte, MaxStack)
	n := runtime.Stack(trace, false)
	if n <= 0 {
		return ""
	}
	return string(trace[:n])
}

func Recovery() gin.HandlerFunc {
	return func(ctx *gin.Context) {
		defer func() {
			if err := recover(); err != nil {
				log.WarnF("Recovered from %v, stack: %v", ctx.Request.URL.Path, Stack())
				ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{"code": http.StatusInternalServerError, "message": "system error", "certificationStatus": "-1"})
			}
		}()
		ctx.Next()
	}
}
