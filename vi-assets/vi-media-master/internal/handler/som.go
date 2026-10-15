package handler

import (
	"github.com/gin-gonic/gin"
	"net/http"
	"strconv"
	"strings"
	"vi-media/internal/conf"
	"vi-media/internal/dto"
	"vi-media/internal/pkg/cache"
	"vi-media/internal/pkg/kv/registry"
	"vi-media/internal/pkg/log"
	"vi-media/internal/proxy"
)

type SomHandler struct{}

func (h *SomHandler) Config(ctx *gin.Context) {
	configValue, err := strconv.Atoi(ctx.Query("value"))
	if err != nil || configValue < 0 {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid value"))
		return
	}
	switch ctx.Param("config") {
	case "cMemLifeSpan":
		conf.App.Storage.Data.Mem.LifeSpan.Call = configValue
		cache.UpdateLifeSpan(cache.ZoneC, cache.LevelMem, configValue)
	case "cDiskLifeSpan":
		conf.App.Storage.Data.Disk.LifeSpan.Call = configValue
		cache.UpdateLifeSpan(cache.ZoneC, cache.LevelDisk, configValue)
	case "expiredScan":
		fetchSize, fErr := strconv.Atoi(ctx.Query("fetch"))
		if fErr != nil || fetchSize < configValue {
			ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid fetchSize"))
			return
		}
		cache.UpdateExpiredScanConfig(fetchSize, configValue)
	case "flush":
		if configValue > 5 {
			ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid value"))
			return
		}
		cache.DoExpiration(configValue)
	case "logLevel":
		if configValue == 0 {
			conf.App.Logger.Level = "debug"
			_ = log.Level("debug")
		} else {
			conf.App.Logger.Level = "info"
			_ = log.Level("info")
		}
	case "audioTimeout":
		if configValue > 0 {
			conf.App.Cache.Cdn.AudioTimeout = configValue
		}
	case "videoTimeout":
		if configValue > 0 {
			conf.App.Cache.Cdn.VideoTimeout = configValue
		}
	default:
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid param"))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *SomHandler) ServiceStatus(ctx *gin.Context) {
	switch ctx.Param("ops") {
	case "pause":
		registry.TurnMaintenance()
	case "resume":
		registry.TurnUp()
	case "status":
		ctx.JSON(http.StatusOK, dto.SuccessWithData(http.StatusOK, registry.Status()))
		return
	case "weight":
		weight, err := strconv.Atoi(strings.TrimSpace(ctx.Query("value")))
		if err != nil {
			ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid weight"))
			return
		}
		registry.Limit(weight)
	case "cpu_threshold":
		fTh, err := strconv.ParseFloat(strings.TrimSpace(ctx.Query("value")), 64)
		if err != nil {
			ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid cpu threshold"))
			return
		}
		conf.App.Monitor.CPU.Threshold = fTh
	default:
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid ops"))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *SomHandler) MediaCacheReload(ctx *gin.Context) {
	if err := proxy.Load(strings.TrimSpace(ctx.Query("name"))); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *SomHandler) MediaCacheRewrite(ctx *gin.Context) {
	level, err := strconv.Atoi(ctx.Query("level"))
	if err != nil || level < cache.LevelMem || level > cache.LevelDisk {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid level"))
		return
	}
	err = cache.Rewrite(strings.TrimSpace(ctx.Query("zone")), level)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *SomHandler) MediaCacheLookup(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, dto.SuccessWithData[string](http.StatusOK, proxy.Exists(strings.TrimSpace(ctx.Query("name")))))
}

func (h *SomHandler) MediaCacheElements(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, dto.SuccessWithData[int](http.StatusOK, proxy.Elements()))
}