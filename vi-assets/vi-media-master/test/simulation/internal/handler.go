package internal

import (
	"fmt"
	"github.com/bytedance/sonic"
	"net"
	"net/http"
	"simulation/internal/dto"
	"simulation/pkg/engine"
	"strconv"
	"strings"
	"time"
)

type MediaStream struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Platform ID
	PlatformId string `json:"platformId" binding:"required"`
	// User ID
	UserId string `json:"userId" binding:"required"`
	// Caller number
	CallerNum string `json:"callerNum" binding:"required"`
	// Callee number
	CalleeNum string `json:"calleeNum" binding:"required"`
	// Secret number
	SecretNum string `json:"secretNum" binding:"required"`
	// The authenticated account
	AppKey string `json:"appKey"`
	// The authenticated secret
	AppSecret string `json:"appSecret"`
	// Start time, the time when the initial signaling is received
	StartTime string `json:"startTime" binding:"required"`
	// Stream period: Early、InCall、Full
	Period string `json:"period" binding:"required,oneof=Early InCall Full"`
	// Business form: RAW、ASR
	Mode string `json:"mode" binding:"required,oneof=RAW ASR"`
	// When the Mode is ASR, the Language must be sent
	// Supported zh、en、cn_henanese
	Language string `json:"language"`
	// The remote receiving end URL
	Url string `json:"url" binding:"required"`
	// The data for the user
	UserData string `json:"userData"`
	// Stream source:
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	Source uint8 `json:"source" binding:"required,oneof=1 2 3"`
	// Whether the caller and callee each transfer separately
	Separated bool `json:"separated"`
	// Based on timeliness or completeness
	Timeliness bool `json:"timeliness"`
	// Sample Rate
	SampleRate int `json:"sampleRate" binding:"required,oneof=8000 16000"`
	// Initial segments, unit seconds
	InitialSegments int `json:"initialSegments"`
	// Time segments, unit seconds
	Segments int `json:"segments"`
	// Call time
	CallTime int64 `json:"callTime"`
}

func SessionCreate(ctx *engine.Context) {
	sessionId := ctx.GetString(engine.ContextSessionId)
	startTime := time.Now().Format("2006-01-02 15:04:05")
	status, data, err := post(urls[KeySessionCreate], dto.SessionCreate{
		SessionId:   sessionId,
		AppId:       "111",
		CallerNum:   "13815936899",
		CalleeNum:   "15813255469",
		MergeConfig: "{\"recordSuffix\":\"mp3\",\"reverseLeg\":false,\"separateLeg\":false,\"isRecord\":true,\"separateEarly\":true,\"bindId\":\"axbtest0123456789012345678901234\"}",
		StartTime:   startTime,
		CallbackUrl: urls[KeyMediaNotice],
		Direction:   3,
		RecordMode:  1,
		SeqId:       1,
	}, http.Header{"X-Session-Id": []string{sessionId}, "X-Sequence-Id": []string{"1"}})
	if err != nil {
		log.WarnK(sessionId, "Session create failed: %v", err)
		abort(ctx, 1)
		return
	}
	if status != http.StatusOK {
		log.WarnK(sessionId, "Session create failed: status", status)
		abort(ctx, 1)
		return
	}

	var resp dto.Result[dto.SessionCreateResp]
	err = sonic.Unmarshal(data, &resp)
	if err != nil {
		abort(ctx, 1)
		return
	}
	log.InfoK(sessionId, "Session create success: %v", string(data))
	ctx.Set(KeyVersion, 1)
	ctx.Set(KeySequence, 2)
	ctx.Set(KeyAudioPort, resp.Data.AudioPort)
	ctx.Set(KeyVideoPort, resp.Data.VideoPort)
	ctx.Set(KeyMimeType, config.audioMimeType[0])
	ctx.Set(KeyStartTime, startTime)
}

func SessionUpdate(ctx *engine.Context) {
	seq := ctx.GetInt(KeySequence)
	base := ctx.GetInt(engine.ContextPort)
	flag := ctx.GetString(KeyPickupFLag)
	sessionId := ctx.GetString(engine.ContextSessionId)
	params := dto.SessionUpdate{
		SessionId:       sessionId,
		AudioCallerIp:   config.ImsHost,
		AudioCalleeIp:   config.ImsHost,
		AudioCode:       strings.TrimPrefix(ctx.GetString(KeyMimeType), "audio/"),
		AudioCallerPort: base,
		AudioCalleePort: base + 2,
		SeqId:           uint8(seq),
	}
	params.AudioPayload = audioPayloads["audio/"+params.AudioCode]
	status, _, err := post(urls[KeySessionUpdate], params, http.Header{"X-Session-Id": []string{sessionId}, "X-Sequence-Id": []string{strconv.Itoa(int(seq))}})
	if err != nil {
		log.WarnK(sessionId, "Session update failed: %v", err)
		abort(ctx, seq)
		return
	}
	if status != http.StatusOK {
		log.WarnK(sessionId, "Session update failed: status", status)
		abort(ctx, seq)
		return
	}
	log.InfoK(sessionId, "Session update success")
	if flag != KeySessionPickup {
		log.InfoK(sessionId, "Start simulating audio transfer")
		// Audio listen and transfer
		server, _ := net.ResolveUDPAddr("udp", fmt.Sprintf("%s:%d", config.ImsServer, ctx.GetInt(KeyAudioPort)))
		audioCallerConn, lErr := Listen(ctx, base)
		if lErr != nil {
			log.WarnK(ctx.GetString(engine.ContextSessionId), "Listen audio<%s:%d> failed: %v", config.ImsHost, base, lErr)
			abort(ctx, seq)
			return
		}
		audioCalleeConn, lErr := Listen(ctx, base+2)
		if lErr != nil {
			log.WarnK(ctx.GetString(engine.ContextSessionId), "Listen audio<%s:%d> failed: %v", config.ImsHost, base+2, lErr)
			abort(ctx, seq)
			return
		}
		ch1, ch2, ch3 := make(chan string, 1), make(chan string, 1), make(chan string, 1)
		ctx.Set(TrackCaller, ch1)
		ctx.Set(TrackCallee, ch2)
		ctx.Set(KeyEnd, ch3)
		ctx.Async(func() {
			AudioTransfer(ctx, audioCallerConn, server, TrackCaller, func(mime string, version int) int { return dynamicSessionUpdate(ctx, mime, version) })
		})
		ctx.Async(func() {
			AudioTransfer(ctx, audioCalleeConn, server, TrackCallee, func(mime string, version int) int { return dynamicSessionUpdate(ctx, mime, version) })
		})
	}
	ctx.Set(KeySequence, seq+1)
}

func SessionPickup(ctx *engine.Context) {
	seq := ctx.GetInt(KeySequence)
	sessionId := ctx.GetString(engine.ContextSessionId)
	status, _, err := post(urls[KeySessionPickup], dto.SessionPickup{
		SessionId: sessionId,
		StartTime: time.Now().Format("2006-01-02 15:04:05"),
		SeqId:     uint8(seq),
	}, http.Header{"X-Session-Id": []string{sessionId}, "X-Sequence-Id": []string{strconv.Itoa(int(seq))}})
	if err != nil {
		log.WarnK(sessionId, "Session pickup failed: %v", err)
		abort(ctx, seq)
		return
	}
	if status != http.StatusOK {
		log.WarnK(sessionId, "Session pickup failed: %v", status)
		abort(ctx, seq)
		return
	}
	log.InfoK(sessionId, "Session pickup success")
	ctx.Set(KeyPickupFLag, KeySessionPickup)
	ctx.Set(KeySequence, seq+1)
	v1, _ := ctx.Get(TrackCaller)
	v2, _ := ctx.Get(TrackCallee)
	v1.(chan string) <- KeySessionPickup
	v2.(chan string) <- KeySessionPickup
	e, _ := ctx.Get(KeyEnd)
	<-e.(chan string)
}

func SessionStop(ctx *engine.Context) {
	seq := ctx.GetInt(KeySequence)
	sessionId := ctx.GetString(engine.ContextSessionId)
	status, _, err := post(urls[KeySessionStop], dto.SessionStop{
		SessionId: sessionId,
		EndTime:   time.Now().Format("2006-01-02 15:04:05"),
		SeqId:     uint8(seq),
	}, http.Header{"X-Session-Id": []string{sessionId}, "X-Sequence-Id": []string{strconv.Itoa(int(seq))}})
	if err != nil {
		log.WarnK(sessionId, "Session stop failed: %v", err)
		return
	}
	if status != http.StatusOK {
		log.WarnK(sessionId, "Session stop failed: %v", status)
		return
	}
	log.InfoK(sessionId, "Session stop success")
	ctx.Set(KeySequence, seq+1)
}

func MediaInspect(ctx *engine.Context) {
	seq := ctx.GetInt(KeySequence)
	sessionId := ctx.GetString(engine.ContextSessionId)
	startTime := ctx.GetString(KeyStartTime)
	data, _ := sonic.Marshal(&MediaStream{SessionId: sessionId, PlatformId: "111", UserId: "111", CallerNum: "13815936899", CalleeNum: "15813255469", SecretNum: "70012346134",
		StartTime: startTime, Period: "InCall", Mode: "RAW", Url: "INSPECT", Source: 3, SampleRate: config.StreamSampleRate, Segments: config.StreamSegments, InitialSegments: config.StreamStartFrames})
	status, _, err := post(urls[KeyMediaInspect], dto.MediaInspect{
		SessionId: sessionId,
		Config:    string(data),
		Frames:    config.StreamStartFrames,
		Direction: 3,
		SeqId:     uint8(seq),
	}, http.Header{"X-Session-Id": []string{sessionId}, "X-Sequence-Id": []string{strconv.Itoa(int(seq))}})
	if err != nil {
		log.WarnK(sessionId, "Media inspect notice failed: %v", err)
		abort(ctx, seq)
		return
	}
	if status != http.StatusOK {
		log.WarnK(sessionId, "Media inspect notice failed: %v", status)
		abort(ctx, seq)
		return
	}
	log.InfoK(sessionId, "Media inspect notice success")
	ctx.Set(KeySequence, seq+1)
}

func dynamicSessionUpdate(ctx *engine.Context, mime string, version int) int {
	ver, ok := ctx.Inc(KeyVersion, version)
	if ok {
		ctx.Set(KeyMimeType, mime)
		SessionUpdate(ctx)
	}
	return ver
}

func abort(ctx *engine.Context, seq int) {
	ctx.Set(KeySequence, seq+1)
	SessionStop(ctx)
	ctx.Abort()
}