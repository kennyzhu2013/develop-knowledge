package handler

import (
	"github.com/gin-gonic/gin"
	"net/http"
	"vi-media/internal/dto"
	"vi-media/internal/pkg/cache"
)

type MediaHandler struct{}

func (h *MediaHandler) Download(ctx *gin.Context) {
	data, err := cache.Get(cache.ZoneC, ctx.Param("filename"))
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.Data(http.StatusOK, "application/octet-stream", data)
}

func (h *MediaHandler) Delete(ctx *gin.Context) {
	err := cache.Delete(cache.ZoneC, ctx.Param("filename"))
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}