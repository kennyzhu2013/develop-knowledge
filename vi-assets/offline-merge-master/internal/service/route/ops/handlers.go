package ops

import (
	"fmt"
	"net/http"
	"offline-merge/conf"
	"offline-merge/internal/pkg/broker"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/pkg/registry"
	"strconv"

	"github.com/gin-gonic/gin"
)

type OMHandler struct{}

func (o *OMHandler) Config(ctx *gin.Context) {
	key := ctx.Param("config")
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
			log.WarnK("10066", "the conf logLevel is invalid")
			ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the conf logLevel is invalid"})
			return
		}
	case "fileAppName":
		v := ctx.Query("value")
		if len(v) > 10 {
			conf.App.Outbound.FileAppName = v
		} else {
			log.WarnK("10066", "the fileAppName is invalid")
			ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the fileAppName is invalid"})
			return
		}
	case "uploadFailSaveLocal":
		conf.App.Outbound.SaveLocal = configValue == 1
		log.WarnK("10086", fmt.Sprintf("SaveLocal=%v", configValue))
	default:
		ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the conf param is invalid"})
		return
	}
	ctx.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func (o *OMHandler) ConfigManifest(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, gin.H{"LogLevel": conf.App.LogLevel,
		"fileAppName": conf.App.Outbound.FileAppName,
		"viTopic":     conf.App.ViTopic,
		"mergeTopic":  conf.App.MergeTopic,
		"dccTopic":    conf.App.DccTopic,
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
	case "stop":
		registry.Close()
	default:
		ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the ops param is invalid"})
		return
	}
	ctx.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func (o *OMHandler) MqOpsStatus(ctx *gin.Context) {
	opsValue := ctx.Param("ops")
	log.InfoF("mq,opsValue,ops=%v", opsValue)
	switch opsValue {
	case "stop":
		broker.MqGracefulStop()
	case "start":
		broker.StartConsumer()
	default:
		ctx.AbortWithStatusJSON(http.StatusBadRequest, gin.H{"code": http.StatusBadRequest, "message": "the mq ops param is invalid"})
		return
	}
	ctx.JSON(http.StatusOK, gin.H{"message": "ok"})
}
