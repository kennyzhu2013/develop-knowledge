package middleware

import (
	"bytes"
	"github.com/gin-gonic/gin"
	"io"
	"strings"
	"vi-media/internal/pkg/log"
)

type AccessLogWriter struct {
	gin.ResponseWriter
	responseBody bytes.Buffer
}

func (w *AccessLogWriter) Write(data []byte) (n int, err error) {
	contentType := w.ResponseWriter.Header().Get("Content-Type")
	if strings.Contains(contentType, "application/json") || strings.Contains(contentType, "application/sdp") {
		if n, err = w.responseBody.Write(data); err != nil {
			return n, err
		}
	}
	return w.ResponseWriter.Write(data)
}

func (w *AccessLogWriter) WriteString(s string) (n int, err error) {
	contentType := w.ResponseWriter.Header().Get("Content-Type")
	if strings.Contains(contentType, "application/json") || strings.Contains(contentType, "application/sdp") {
		if n, err = w.responseBody.WriteString(s); err != nil {
			return n, err
		}
	}
	return w.ResponseWriter.WriteString(s)
}

func AccessLog() gin.HandlerFunc {
	return func(ctx *gin.Context) {
		requestUri := ctx.Request.URL.RequestURI()
		if strings.Contains(requestUri, "metrics") || strings.Contains(requestUri, "pprof") {
			ctx.Next()
			return
		}
		contentType := ctx.GetHeader("Content-Type")
		if strings.HasPrefix(contentType, "multipart/form-data") || strings.HasPrefix(contentType, "application/octet-stream") {
			log.InfoK(ctx.GetHeader("X-Session-Id"), "[%s] - [%s]", ctx.Request.Method, requestUri)
		} else {
			body, _ := ctx.GetRawData()
			log.InfoK(ctx.GetHeader("X-Session-Id"), "[%s] - [%s] - RequestBody: %s", ctx.Request.Method, requestUri, string(body))
			ctx.Request.Body = io.NopCloser(bytes.NewBuffer(body))
		}
		writer := &AccessLogWriter{ResponseWriter: ctx.Writer}
		ctx.Writer = writer
		ctx.Next()
		log.InfoK(ctx.GetHeader("X-Session-Id"), "[%s] - [%d] - ResponseBody: %s", requestUri, ctx.Writer.Status(), writer.responseBody.String())
	}
}