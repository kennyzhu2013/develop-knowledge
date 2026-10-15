package handler

import (
	"github.com/gin-gonic/gin"
	"net/http"
	"strconv"
	"strings"
	"vi-media/internal/conf"
	"vi-media/internal/dto"
	"vi-media/internal/proxy"
)

const ctxSession = "Session"
const headerSessionId = "X-Session-Id"
const headerSequenceId = "X-Sequence-Id"

type ProxyHandler struct{}

func (h *ProxyHandler) Session(ctx *gin.Context) {
	sessionId := strings.TrimSpace(ctx.GetHeader(headerSessionId))
	if len(sessionId) == 0 {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid sessionId"))
		return
	}
	seqId := strings.TrimSpace(ctx.GetHeader(headerSequenceId))
	if len(seqId) == 0 {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid sequenceId"))
		return
	}
	id, err := strconv.Atoi(seqId)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "invalid sequenceId"))
		return
	}
	session, ok := proxy.GetManager().Get(sessionId)
	if !ok {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "session is not exists"))
		return
	}
	ctx.Set(ctxSession, session)
	ctx.Next()
	// Fallback
	if ctx.IsAborted() && ctx.Writer.Status() != http.StatusInternalServerError {
		_ = session.Fallback(uint8(id))
	}
}

func (h *ProxyHandler) SessionCreate(ctx *gin.Context) {
	var message dto.SessionCreate
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	if _, ok := proxy.GetManager().Get(message.SessionId); ok {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, "session already exists"))
		return
	}
	sess := proxy.GetManager().GetFree(message.SessionId)
	if sess == nil {
		ctx.AbortWithStatusJSON(http.StatusServiceUnavailable, dto.Fail(http.StatusServiceUnavailable, "session has reached a limit"))
		return
	}
	if err = sess.Create(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.SuccessWithData[dto.SessionCreateResp](http.StatusOK, dto.SessionCreateResp{
		SessionId: message.SessionId,
		IP:        conf.App.Ims.Host,
		AudioPort: sess.AudioPort(),
		VideoPort: sess.VideoPort(),
		SeqId:     message.SeqId,
	}))
}

func (h *ProxyHandler) SessionUpdate(ctx *gin.Context) {
	var message dto.SessionUpdate
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).Update(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) SessionPickup(ctx *gin.Context) {
	var message dto.SessionPickup
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).Pickup(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) SessionStop(ctx *gin.Context) {
	var message dto.SessionStop
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).Stop(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) MediaInspect(ctx *gin.Context) {
	var message dto.MediaInspect
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).MediaInspect(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) MediaInspectNotice(ctx *gin.Context) {
	var message dto.MediaInspectNotice
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).MediaInspectNotice(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) MediaStream(ctx *gin.Context) {
	var message dto.MediaStream
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).MediaStream(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) MediaDtmf(ctx *gin.Context) {
	var message dto.MediaDtmf
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).MediaDtmf(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) MediaPlay(ctx *gin.Context) {
	var message dto.MediaPlay
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	err = (&message).Validate()
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).MediaPlay(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}

func (h *ProxyHandler) MediaStop(ctx *gin.Context) {
	var message dto.MediaStop
	err := ctx.ShouldBindJSON(&message)
	if err != nil {
		ctx.AbortWithStatusJSON(http.StatusBadRequest, dto.Fail(http.StatusBadRequest, err.Error()))
		return
	}
	session, _ := ctx.Get(ctxSession)
	if err = session.(*proxy.Session).MediaStop(&message); err != nil {
		ctx.AbortWithStatusJSON(http.StatusInternalServerError, dto.Fail(http.StatusInternalServerError, err.Error()))
		return
	}
	ctx.JSON(http.StatusOK, dto.Success(http.StatusOK))
}