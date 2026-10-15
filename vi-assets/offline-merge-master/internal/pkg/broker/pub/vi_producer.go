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

type ViBillMessage struct {
	// Session unique ID
	SessionId string `json:"sessionId"`

	//分片ID
	SegmentID uint32 `json:"segmentId"`

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
	Duration float32 `json:"duration"`

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

	//AS传过来的数据
	UserData string `json:"userData"`

	Fallback bool `json:"fallback"`
}

func (m *ViBillMessage) String() string {
	return fmt.Sprintf("{sessionId: %v, SegmentID: %v, CallerNum: %v, CalleeNum: %v, UserId: %v, FileNode: %v, Version: %v, "+
		"EndFlag: %v, Duration: %v,  Path: %v, Volume: %v, FileName: %v, Suffix: %v ,IsStream: %v ,Fallback: %v, UserData:[%v]}",
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
		m.Fallback,
		m.UserData,
	)
}

type ViProducer struct {
	once   sync.Once
	prod   mq.Producer
	pTopic string
}

func NewViProducer(topic string) *ViProducer {
	return &ViProducer{
		pTopic: topic,
	}
}

func (vi *ViProducer) InitProducer() error {
	producer, err := clientProducer(vi.pTopic)
	if err != nil {
		log.WarnK("1008611", "ViProducer,clientProducer,err=%v", err)
		return err
	}
	vi.prod = producer
	return nil
}

func (vi *ViProducer) PubBillMessage(sId string, data interface{}) error {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(sId, "ViProducer,Recovered from broker push with %v", v)
		}
	}()

	if vi.prod == nil {
		//return errors.New("ViProducer,init fail")
		err := vi.InitProducer()
		if err != nil {
			log.WarnK(sId, "ViProducer,PubBillMessage,InitProducer,err=%v", err)
			return err
		}
	}

	viMsg, ok := data.(*ViBillMessage)
	if !ok {
		log.WarnK(sId, "ViProducer,ViBillMessage,invalid data type")
		return errors.New("ViProducer,invalid data")
	}

	bodyJson, err := json.Marshal(viMsg)
	if err != nil {
		log.WarnK(sId, "ViProducer,ViBillMessage,Marshal,err=%v", err)
		return err
	}

	message := &mq.Message{
		Topic: vi.pTopic,
		Body:  bodyJson,
	}
	message.SetTag("*")
	message.SetKeys(sId)

	// 增加超时设置
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	sendReceipts, err := vi.prod.Send(ctx, message)
	if err == nil && len(sendReceipts) > 0 {
		log.InfoK(sId, "ViProducer,sendSuc,msgId=%v,message=[%v]", sendReceipts[0].MessageID, string(bodyJson))
	}
	return err
}

func (vi *ViProducer) StopProducer() error {
	var err error

	if vi.prod != nil {
		vi.once.Do(func() {
			err = vi.prod.GracefulStop()
			log.WarnF("viProducer,GracefulStop,err=%v", err)
		})
	}
	return err
}
