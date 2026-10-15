package broker

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sync"
	"time"
	"vi-merge/internal/pkg/log"

	mq "github.com/apache/rocketmq-clients/golang/v5"
	"github.com/apache/rocketmq-clients/golang/v5/credentials"
)

type BillMessage struct {
	// Session unique ID
	SessionId string `json:"sessionId"`

	//分片ID
	SegmentID *uint32 `json:"segmentId,omitempty"`

	// Caller num
	CallerNum string `json:"callerNum"`

	// Callee num
	CalleeNum string `json:"calleeNum"`

	// User ID（企业Id）
	UserId string `json:"userId"`

	//file://tmp/a/b/c.wav or  http://10.135.90.12:9092/a/b/c/c_e_f.wav
	FileNode string `json:"fileNode"`

	//version
	Version string `json:"version,omitempty"`

	//非必须，true就是最后一个分片
	EndFlag bool `json:"endFlag"`

	//计算录音总时长
	Duration float64 `json:"duration"`

	//相对路径名
	Path string `json:"path"`

	//文件名
	FileName string `json:"fileName"`

	//pfs的root
	Volume string `json:"volume"`

	//实时质检wav，离线可以有mp3或者wav
	Suffix string `json:"suffix"`

	//是否实时流
	IsStream bool `json:"isStream"`

	UploadFlag bool `json:"uploadFlag"`

	// 全录音标识
	FullAudio bool `json:"fullAudio,omitempty"`

	// AS传过来的数据
	UserData string `json:"userData"`
}

func (m *BillMessage) String() string {
	return fmt.Sprintf("{sessionId: %v, SegmentID: %v, CallerNum: %v, CalleeNum: %v, UserId: %v, fileNode：%v Version: %v, "+
		"EndFlag: %v, Duration: %v, Path: %v, Volume: %v, FileName: %v, Suffix: %v ,IsStream: %v , UserData:[%v]}",
		m.SessionId,
		m.SegmentID,
		m.CallerNum,
		m.CalleeNum,
		m.UserId,
		m.FileNode,
		m.Version,
		m.EndFlag,
		m.Duration,
		m.Path,
		m.Volume,
		m.FileName,
		m.Suffix,
		m.IsStream,
		m.UserData,
	)
}

var once sync.Once
var prod mq.Producer

var (
	namespaceP, topicP, accessKeyP, secretKeyP, endpointP string
	maxAttemptsP                                          int
)

func Init(namespace, topic, accessKey, secretKey, endpoint string, maxAttempts int) error {
	namespaceP = namespace
	topicP = topic
	accessKeyP = accessKey
	secretKeyP = secretKey
	endpointP = endpoint
	maxAttemptsP = maxAttempts

	return initProducer()
}

func initProducer() error {
	producer, err := mq.NewProducer(&mq.Config{
		Endpoint:  endpointP,
		NameSpace: namespaceP,
		Credentials: &credentials.SessionCredentials{
			AccessKey:    accessKeyP,
			AccessSecret: secretKeyP,
		},
	}, mq.WithMaxAttempts(int32(maxAttemptsP)), mq.WithTopics(topicP))
	if err != nil {
		return err
	}
	err = producer.Start()
	if err != nil {
		return err
	}
	prod = producer
	return nil
}

func PubBillMessage(sId string, msg *BillMessage) error {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(msg.SessionId, "Recovered from broker push with %v", v)
		}
	}()

	if prod == nil {
		err := initProducer()
		if err != nil {
			return errors.New("producer,init fail")
		}
	}
	body, err := json.Marshal(msg)
	if err != nil {
		log.InfoK(sId, "PubBillMessage,Marshal,fail,err=%v", err)
	}

	message := &mq.Message{
		Topic: topicP,
		Body:  body,
	}

	//增加超时设置
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	message.SetTag("*")
	message.SetKeys(msg.SessionId)
	sendReceipts, err := prod.Send(ctx, message)
	if err == nil && len(sendReceipts) > 0 {
		log.InfoK(sId, "PubBillMessage,sendSuc,msgId=%v,Json=[%v]", sendReceipts[0].MessageID, string(body))
	}
	return err
}

func Stop() error {
	var err error
	once.Do(func() {
		err = prod.GracefulStop()
	})
	return err
}
