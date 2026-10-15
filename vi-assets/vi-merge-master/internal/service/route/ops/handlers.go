package ops

import (
	"fmt"
	"net/http"
	"strconv"
	"vi-merge/conf"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/pkg/registry"

	"github.com/gin-gonic/gin"
)

type OMHandler struct{}

func (o *OMHandler) Config(ctx *gin.Context) {
	key := ctx.Param("config") //bugfix on 2025-12-25
	var configValue int
	var err error
	if key != "logLevel" {
		configValue, err = strconv.Atoi(ctx.Query("value"))
		if err != nil || configValue < 0 {
			ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the conf value is invalid", "certificationStatus": "-1"})
			return
		}
	}
	switch key {
	case "logLevel":
		err = log.Level(ctx.Query("value"))
		if err != nil {
			log.WarnK("10086", "the conf logLevel is invalid")
			ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the conf logLevel is invalid"})
			return
		}
	case "maxSession":
		if configValue < 200 || configValue > 5000 {
			log.WarnK("10086", "the conf maxSession is invalid")
			ctx.JSON(http.StatusBadRequest, gin.H{"message": "the conf param is invalid"})
			return
		}
		conf.App.MaxSession = configValue
		log.WarnK("10086", fmt.Sprintf("MaxSession=%v", configValue))
	case "buffer2DiskMinutes":
		conf.App.Buffer2DiskMinutes = configValue
		log.WarnK("10086", fmt.Sprintf("buffer2DiskMinutes=%v", configValue))
	case "diskRwMax":
		conf.App.DiskRwMax = configValue
		log.WarnK("10086", fmt.Sprintf("diskRwMax=%v", configValue))
	case "decisionType":
		conf.App.DecisionType = configValue
		log.WarnK("10086", fmt.Sprintf("decisionType=%v", configValue))
	case "memUsedPercent":
		conf.App.MemUsedPercent = configValue
		log.WarnK("10086", fmt.Sprintf("memUsedPercent=%v", configValue))
	case "finalSuffixMp3":
		conf.App.FinalSuffixMp3 = configValue == 1
		log.WarnK("10086", fmt.Sprintf("finalSuffixMp3=%v", configValue))
	default:
		ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the conf param is invalid"})
		return
	}
	ctx.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func (o *OMHandler) ConfigManifest(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, gin.H{
		"LogLevel":           conf.App.LogLevel,
		"MaxSession":         conf.App.MaxSession,
		"Topic":              conf.App.Topic,
		"Buffer2DiskMinutes": conf.App.Buffer2DiskMinutes,
		"DiskRwMax":          conf.App.DiskRwMax,
		"DecisionType":       conf.App.DecisionType,
		"MemUsedPercent":     conf.App.MemUsedPercent,
	})
}

func (o *OMHandler) OMUServiceStatus(ctx *gin.Context) {
	switch ctx.Param("ops") {
	case "status":
		ctx.JSON(http.StatusOK, registry.Status())
		return
	case "pause":
		registry.TurnMaintenance()
	case "resume":
		registry.TurnUp()
	default:
		ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the ops param is invalid"})
		return
	}
	ctx.JSON(http.StatusOK, gin.H{"message": "ok"})
}
