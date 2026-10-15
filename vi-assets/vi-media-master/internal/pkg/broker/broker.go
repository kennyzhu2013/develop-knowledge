package broker

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"context"
	"encoding/json"
	"fmt"
	mq "github.com/apache/rocketmq-clients/golang/v5"
	"github.com/apache/rocketmq-clients/golang/v5/credentials"
	"sync"
	"time"
	"vi-media/internal/pkg/log"
)

type MediaMergeMessage struct {
	// Session unique ID
	SessionId string `json:"sessionId"`
	// Caller num
	CallerNum string `json:"callerNum"`
	// Callee num
	CalleeNum string `json:"calleeNum"`
	// User ID
	UserId string `json:"userId"`
	// Media application ID
	MsId string `json:"msId"`
	// Media application address
	Address string `json:"address"`
	// Media application version
	Version string `json:"version"`
	// Storage volume
	Volume string `json:"volume"`
	// Relative storage sub path
	Path string `json:"path"`
	// A list of caller audio filenames, separated by commas
	CallerAudioFile string `json:"callerAudioFile"`
	// A list of callee audio filenames, separated by commas
	CalleeAudioFile string `json:"calleeAudioFile"`
	// A list of caller video filenames, separated by commas
	CallerVideoFile string `json:"callerVideoFile"`
	// A list of callee video filenames, separated by commas
	CalleeVideoFile string `json:"calleeVideoFile"`
	// A list of early audio filenames, separated by commas
	EarlyAudioFile string `json:"earlyAudioFile"`
	// Merge configuration, in JSON format
	Options string `json:"options"`
	// Inspect configuration
	Config string `json:"config"`
	// Start time, the time when the initial signaling is received
	StartTime int64 `json:"startTime"`
	// Whether to downgrade
	Fallback bool `json:"fallback"`
	// The total audio rtp sending of the platform or the side, include ringing and inCall
	CallerAudioRtpCount int `json:"callerAudioRtpCount"`
	CalleeAudioRtpCount int `json:"calleeAudioRtpCount"`
	// The total audio rtp sending of the platform or the side in calling
	CallerAudioInCallRtpCount int `json:"callerAudioInCallRtpCount"`
	CalleeAudioInCallRtpCount int `json:"calleeAudioInCallRtpCount"`
	// The audio recording success count of the platform or the side
	CallerAudioRecordCount int `json:"callerAudioRecordCount"`
	CalleeAudioRecordCount int `json:"calleeAudioRecordCount"`
	// The total video rtp sending of the platform or the side, include ringing and inCall
	CallerVideoRtpCount int `json:"callerVideoRtpCount"`
	CalleeVideoRtpCount int `json:"calleeVideoRtpCount"`
	// The video recording success count of the platform or the side
	CallerVideoRecordCount int `json:"callerVideoRecordCount"`
	CalleeVideoRecordCount int `json:"calleeVideoRecordCount"`
}

func (m *MediaMergeMessage) String() string {
	return fmt.Sprintf("{AppId: %v, MsId: %v, Address: %v, Version: %v, Volume: %v, Path: %v, CallerAudioFile: %v, CalleeAudioFile: %v, CallerVideoFile: %v, "+
		"CalleeVideoFile: %v, EarlyAudioFile: %v, Options: %v, Fallback: %v, CallerAudioRtpCount: %v, CalleeAudioRtpCount: %v, CallerAudioInCallRtpCount: %v, CalleeAudioInCallRtpCount: %v, "+
		"CallerAudioRecordCount: %v, CalleeAudioRecordCount: %v, CallerVideoRtpCount: %v, CalleeVideoRtpCount: %v, CallerVideoRecordCount: %v, CalleeVideoRecordCount: %v}",
		m.UserId,
		m.MsId,
		m.Address,
		m.Version,
		m.Volume,
		m.Path,
		m.CallerAudioFile,
		m.CalleeAudioFile,
		m.CallerVideoFile,
		m.CalleeVideoFile,
		m.EarlyAudioFile,
		m.Options,
		m.Fallback,
		m.CallerAudioRtpCount,
		m.CalleeAudioRtpCount,
		m.CallerAudioInCallRtpCount,
		m.CalleeAudioInCallRtpCount,
		m.CallerAudioRecordCount,
		m.CalleeAudioRecordCount,
		m.CallerVideoRtpCount,
		m.CalleeVideoRtpCount,
		m.CallerVideoRecordCount,
		m.CalleeVideoRecordCount)
}

var once sync.Once
var prod mq.Producer
var pTopic string

func Init(namespace, topic, accessKey, secretKey, endpoint string, maxAttempts int) error {
	producer, err := mq.NewProducer(&mq.Config{
		Endpoint:  endpoint,
		NameSpace: namespace,
		Credentials: &credentials.SessionCredentials{
			AccessKey:    accessKey,
			AccessSecret: secretKey,
		},
	}, mq.WithMaxAttempts(int32(maxAttempts)), mq.WithTopics(topic))
	if err != nil {
		return err
	}
	err = producer.Start()
	if err != nil {
		return err
	}
	prod = producer
	pTopic = topic
	return nil
}

func PubMergeMessage(msg *MediaMergeMessage) error {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(msg.SessionId, "Recovered from broker push with %v, stack: %v", v, process.Stack())
		}
	}()
	body, _ := json.Marshal(msg)
	message := &mq.Message{
		Topic: pTopic,
		Body:  body,
	}
	message.SetTag("*")
	message.SetKeys(msg.SessionId)
	ctx, cancel := context.WithDeadline(context.TODO(), time.Now().Add(3*time.Second))
	defer cancel()
	_, err := prod.Send(ctx, message)
	return err
}

func Stop() error {
	var err error
	once.Do(func() {
		err = prod.GracefulStop()
	})
	return err
}