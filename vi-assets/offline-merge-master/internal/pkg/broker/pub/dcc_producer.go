package pub

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"offline-merge/internal/pkg/log"
	"sync"
	"time"

	mq "github.com/apache/rocketmq-clients/golang/v5"
)

const (
	TypeSuc  = 0 // 成功
	TypeFail = 1 // 失败
)

type DccBillMessage struct {
	// 响应码 0成功 1失败
	Code int `json:"code"`

	// 呼叫ID
	CallId string `json:"callId"`

	// 文件名(带后缀)
	FileName string `json:"fileName,omitempty"`

	// 左声道文件
	LeftFile string `json:"leftFile,omitempty"`

	// 右声道文件
	RightFile string `json:"rightFile,omitempty"`

	// 合并文件长度
	CombineFileLength int `json:"combineFileLength,omitempty"`

	// 左声道文件长度
	LeftFileLength int64 `json:"leftFileLength,omitempty"`

	// 右声道文件长度
	RightFileLength int64 `json:"rightFileLength,omitempty"`

	// 用户ID
	UserId string `json:"userId,omitempty"`

	// 文件时长
	FileDuration int `json:"fileDuration,omitempty"`

	// 完整时长
	FullDuration int `json:"fullDuration,omitempty"`

	// 提前时长
	EarlyDuration int `json:"earlyDuration,omitempty"`

	// 音频编码格式
	Acodec int `json:"acodec,omitempty"`

	// 建议改为msId，当前为serverId
	ServerId string `json:"serverId,omitempty"`

	// 是否提前分离
	SeparateEarly bool `json:"separateEarly,omitempty"`

	// 是否完整录制
	FullRecord bool `json:"fullRecord,omitempty"`

	// 绑定ID
	BindId string `json:"bindIdm,omitempty"`

	// 版本号
	Version string `json:"version,omitempty"`

	// 左声道RTP计数
	LeftRtpCounter int `json:"leftRtpCounter,omitempty"`

	// 右声道RTP计数
	RightRtpCounter int `json:"rightRtpCounter,omitempty"`

	// 左声道RTP写入计数
	LeftRtpWriteCounter int `json:"leftRtpWriteCounter,omitempty"`

	// 右声道RTP写入计数
	RightRtpWriteCounter int `json:"rightRtpWriteCounter,omitempty"`
}

func (dc *DccBillMessage) String() string {
	return fmt.Sprintf(
		"BillMsg{Code=%v, CallId=%v, FileName=%v, "+
			"LeftFile=%v, RightFile=%v, CombineFileLength=%v, LeftFileLength=%v, RightFileLength=%v, UserId=%v, FileDuration=%v, FullDuration=%v, "+
			"EarlyDuration=%v, Acodec=%v, ServerId=%v, SeparateEarly=%v, FullRecord=%t, BindId=%v, Version=%v, LeftRtpCounter=%v, RightRtpCounter=%v, "+
			"LeftRtpWriteCounter=%v, RightRtpWriteCounter=%v}",
		dc.Code,
		dc.CallId,
		dc.FileName,
		dc.LeftFile,
		dc.RightFile,
		dc.CombineFileLength,
		dc.LeftFileLength,
		dc.RightFileLength,
		dc.UserId,
		dc.FileDuration,
		dc.FullDuration,
		dc.EarlyDuration,
		dc.Acodec,
		dc.ServerId,
		dc.SeparateEarly,
		dc.FullRecord,
		dc.BindId,
		dc.Version,
		dc.LeftRtpCounter,
		dc.RightRtpCounter,
		dc.LeftRtpWriteCounter,
		dc.RightRtpWriteCounter,
	)
}

type DccProducer struct {
	once   sync.Once
	prod   mq.Producer
	pTopic string
}

func NewDccProducer(topic string) *DccProducer {
	return &DccProducer{
		pTopic: topic,
	}
}

func (d *DccProducer) InitProducer() error {
	producer, err := clientProducer(d.pTopic)
	if err != nil {
		log.WarnK("1008611", "DccProducer,clientProducer,err=%v", err)
		return err
	}
	d.prod = producer
	return nil
}

func (d *DccProducer) PubBillMessage(sId string, data interface{}) error {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(sId, "DccProducer,Recovered from broker push with %v", v)
		}
	}()

	if d.prod == nil {
		//return errors.New("DccProducer,init fail")
		err := d.InitProducer()
		if err != nil {
			log.WarnK(sId, "DccProducer,PubBillMessage,InitProducer,err=%v", err)
			return err
		}
	}

	viMsg, ok := data.(*DccBillMessage)
	if !ok {
		log.WarnK(sId, "DccProducer,DccBillMessage,invalid data type")
		return errors.New("DccProducer,invalid data")
	}

	bodyJson, err := json.Marshal(viMsg)
	if err != nil {
		return err
	}

	message := &mq.Message{
		Topic: d.pTopic,
		Body:  bodyJson,
	}
	message.SetTag("*")
	message.SetKeys(sId)

	//增加超时设置
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	sendReceipts, err := d.prod.Send(ctx, message)
	if err == nil && len(sendReceipts) > 0 {
		log.InfoK(sId, "DccProducer,sendSuc,msgId=%v", sendReceipts[0].MessageID)
	}
	return err
}

func (d *DccProducer) StopProducer() error {
	var err error
	if d.prod != nil {
		d.once.Do(func() {
			err = d.prod.GracefulStop()
			log.WarnF("DccProducer,GracefulStop,err=%v", err)
		})
	}
	return err
}
