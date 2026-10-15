package models

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"vi-merge/internal/pkg/log"
)

const (
	BusinessTypeChat         = "chat"
	BusinessTypeGame         = "game"
	BusinessTypeNotification = "notification"

	//新类型
	BusinessTypeMerge = "merge"

	MergeWav = "mergeWav"
	//20260429确定用EOS（end of stream)
	EVENT_END          = "EOS"
	EVENT_END_INTERNAL = "End_Internal"
)

var (
	EosByte = []byte{69, 79, 83}
)

type BusinessHeader struct {
	BusinessType string
	Version      string
	BizConfig    HeaderParams
	Notice       string
}

func (bu *BusinessHeader) String() string {
	return fmt.Sprintf("{BusinessType=%v,Version=%v,BizConfig={%v}}", bu.BusinessType, bu.Version, bu.BizConfig.String())
}

type HeaderParams struct {
	SessionId string `json:"sessionId,omitempty" binding:"required"`

	Caller string `json:"callerNum,omitempty" binding:"required"`
	Callee string `json:"calleeNum,omitempty" binding:"required"`

	UserId string `json:"userId" binding:"required"`

	//采样率
	SampleRate int `json:"sampleRate" binding:"required,oneof=8000 16000"`

	//默认S16LE
	Fmt string `json:"fmt,omitempty"`

	// The data for the user
	UserData json.RawMessage `json:"userData"`

	UserDataText string `json:"-"`

	// Initial segments, unit seconds
	InitialSegments int `json:"initialSegments" binding:"required"`

	// Time segments, unit seconds
	Segments int `json:"segments"`

	// Start time, the time when the initial signaling is received
	StartTime string `json:"startTime" binding:"required"`

	// Call time
	//CallTime int64 `json:"callTime"`

	// 输入源：1、主叫；2、被叫；3、主被叫
	//	1. Caller
	//	2. Callee
	//	3. Both caller and callee
	Source uint8 `json:"source" binding:"required,oneof=1 2 3"`
}

func (h *HeaderParams) String() string {
	return fmt.Sprintf("{SessionId=%v,Caller=%v,Callee=%v,UserId=%v,SampleRate=%v,Fmt=%v,UserData=[%v]"+
		",InitialSegments=%v,Segments=%v,StartTime=%v,Source=%v}", h.SessionId, h.Caller, h.Callee, h.UserId, h.SampleRate,
		h.Fmt, h.UserDataText, h.InitialSegments, h.Segments, h.StartTime, h.Source)
}

func ParseBusinessHeader(header http.Header) (*BusinessHeader, error) {
	sessionId := strings.TrimSpace(header.Get("X-Session-Id"))
	headConfig := strings.TrimSpace(header.Get("X-Params"))

	///20260611,增加回调地址
	headNotice := strings.TrimSpace(header.Get("X-Notice"))

	var bizConfig HeaderParams
	err := json.Unmarshal([]byte(headConfig), &bizConfig)
	if err != nil {
		log.WarnK(sessionId, "ParseBusinessHeader,headConfig=%v,err=%v", headConfig, err)
		return nil, fmt.Errorf("invalid config,err=%s", err)
	}

	// 方式一：
	var realUserData string
	err = json.Unmarshal(bizConfig.UserData, &realUserData)
	if err != nil {
		log.WarnK(sessionId, "ParseBusinessHeader,UserData,Unmarshal Options,err=%v", err)
		//方式二、
		realUserData = strings.ReplaceAll(string(bizConfig.UserData), "\\", "")
		log.InfoK(sessionId, "ParseBusinessHeader,UserDataText2=%v", realUserData)
	}
	bizConfig.UserDataText = realUserData
	log.InfoK(sessionId, "ParseBusinessHeader,realUserData=%v", realUserData)

	if bizConfig.Source > 3 || bizConfig.Source < 0 {
		log.WarnK(sessionId, "ParseBusinessHeader,Source(%v) err", bizConfig.Source)
		return nil, errors.New("invalid config Source")
	}

	ctx := &BusinessHeader{
		BusinessType: header.Get("X-Business-Type"),
		Version:      header.Get("X-Version"),
		BizConfig:    bizConfig,
		Notice:       headNotice,
	}

	if ctx.BusinessType == "" {
		ctx.BusinessType = BusinessTypeMerge
	}

	//if !ValidateBusinessType(ctx.BusinessType) {
	//	return nil, fmt.Errorf("invalid X-Business-Type: %s", ctx.BusinessType)
	//}

	// 可选字段默认值
	if ctx.Version == "" {
		ctx.Version = "v1"
	}

	if ctx.BizConfig.Segments < 1 {
		//默认是30秒间隔
		ctx.BizConfig.Segments = 30
	}

	if ctx.BizConfig.SampleRate%8000 != 0 {
		log.WarnK(sessionId, "ParseBusinessHeader,SampleRate(%v) err", ctx.BizConfig.SampleRate)
		return ctx, errors.New("invalid config SampleRate")
	}

	return ctx, nil
}

func ValidateBusinessType(bizType string) bool {
	switch bizType {
	case BusinessTypeChat, BusinessTypeGame, BusinessTypeNotification, BusinessTypeMerge:
		return true
	default:
		return false
	}
}

// ------------------- 消息解析器接口（动态解析不同结构体） -------------------
type MessageParser interface {
	// GetBusinessType 对应业务类型
	GetBusinessType() string
	// ParseContent 解析消息内容为对应结构体,msgType是业务类型，增加subMsgType为判断数据类型
	ParseContent(pType string, msgType string, content []byte) (interface{}, error)
}

//--------------------------------------------------------------

// ------------------- BusinessMessage结构体 -------------------
type BusinessMessage struct {
	//内容中的消息类型（子分类）,暂时用不到
	ProType string

	//传输的是二进制还是文本
	MsgType string

	//Content     json.RawMessage  `json:"content"` // 原始内容
	Content        []byte
	SessionId      string
	BusinessHeader *BusinessHeader
}

func EncodeBusinessMessage(msg *BusinessMessage) ([]byte, error) {
	if msg == nil {
		return nil, errors.New("business message is nil")
	}
	return json.Marshal(msg)
}

func DecodeBusinessMessage(messageType int, data []byte) (*BusinessMessage, error) {
	var msg BusinessMessage
	if err := json.Unmarshal(data, &msg); err != nil {
		return nil, fmt.Errorf("decode business message failed: %w", err)
	}
	return &msg, nil
}

//---------------------------------------------------------------

type ErrorMessage struct {
	// 错误码，>=0、正常；<0、异常
	Code int
	// 消息
	Message string
}

func EncodeErrorMessage(msg *ErrorMessage) ([]byte, error) {
	if msg == nil {
		return nil, errors.New("business message is nil")
	}
	return json.Marshal(msg)
}

func DecodeErrorMessage(messageType int, data []byte) (*ErrorMessage, error) {
	var msg ErrorMessage
	if err := json.Unmarshal(data, &msg); err != nil {
		return nil, fmt.Errorf("decode business message failed: %w", err)
	}
	return &msg, nil
}

// ------------------- 各业务专属结构体 -------------------
// 聊天
type ChatSendContent struct {
	From    string `json:"from"`
	To      string `json:"to"`
	Message string `json:"message"`
}
type ChatHeartbeatContent struct {
	Timestamp int64 `json:"timestamp"`
}

// ---start of merge业务----
type MergeContent struct {
	SubType string `json:"-"`

	//TODO:End
	Event string `json:"event"`
	Data  string `json:"data"`

	Source uint   `json:"-"`
	Pcm    []byte `json:"-"`
}

//---end of merge业务----

// 通知业务
type NotificationPushContent struct {
	Title   string `json:"title"`
	Content string `json:"content"`
}

// 通用错误响应
type ErrorContent struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}
