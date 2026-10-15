package middleware

import (
	"bytes"
	"github.com/gin-gonic/gin"
	"io"
	"strings"
	"vi-merge/internal/pkg/log"
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
	return func(c *gin.Context) {
		body, _ := c.GetRawData()
		requestUri := c.Request.URL.RequestURI()
		if strings.Contains(requestUri, "metrics") || strings.Contains(requestUri, "pprof") {
			c.Next()
			return
		}
		log.InfoF("%s %s, requestBody is %s", c.Request.Method, requestUri, string(body))

		writer := &AccessLogWriter{ResponseWriter: c.Writer}
		c.Writer = writer
		c.Request.Body = io.NopCloser(bytes.NewBuffer(body))
		c.Next()

		log.InfoF("%s done with %d, responseBody is %s", requestUri, c.Writer.Status(), writer.responseBody.String())
	}
}
