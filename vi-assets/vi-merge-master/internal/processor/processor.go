package processor

import (
	"fmt"
	"vi-merge/internal/models"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/util"
)

type BusinessProcessor interface {
	GetBusinessType() string
	GetMessageType() string
	Process(connID string, ctx *models.BusinessHeader, data interface{}) error
	Flush(event string) error
}

func GetPath(params models.HeaderParams) string {
	//154/2025-09-19/1758264999
	format, unixTime, err := util.PathTime(params.StartTime)
	if err != nil {
		log.WarnK(params.SessionId, "PathTime,err=%v", err)
		return fmt.Sprintf("%v/%v/%v", params.UserId, params.StartTime, params.SessionId)
	}
	return fmt.Sprintf("%v/%v/%v", params.UserId, format, unixTime)
}
