package notify

import (
	"encoding/json"
	"fmt"
	"math"
	"strconv"
	"vi-merge/internal/pkg/log"
)

func HttpNotifyMedia(sessionID string, NoticeUrl string, errStr string) {
	if len(NoticeUrl) < 3 {
		log.WarnK(sessionID, "HttpNotifyMedia,NoticeUrl too short,errStr=%v", errStr)
		return
	}
	msgStr := fmt.Sprintf(`{"code":"-1000","message":"%v"}`, errStr)
	SendAsync(sessionID, NoticeUrl,
		MediaInspectNotice{
			SessionId: sessionID,
			Message:   errStr,
			Event:     "EXCEPTION",
			Data:      json.RawMessage(msgStr),
		},
		map[string]string{
			"X-Session-Id":  sessionID,
			"X-Sequence-Id": strconv.Itoa(math.MaxInt),
		},
	)
}
