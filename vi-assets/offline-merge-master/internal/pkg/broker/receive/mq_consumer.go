package receive

import (
	"context"
	"errors"
	"fmt"
	"offline-merge/conf"
	"offline-merge/internal/metrics"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/processor"
	"os"
	"strings"
	"sync"
	"time"

	mq "github.com/apache/rocketmq-clients/golang/v5"
	"github.com/apache/rocketmq-clients/golang/v5/credentials"
)

type ConsumerInterface interface {
	Start() error
	GracefulStop() error
}

const (
	SIMPLE = "simple"
	PUSH   = "push"

	// ConsumerGroup = "GID_DCC_LS_TEST"
	// Topic         = "TID_DCC_LS_TEST"
)

var (
	// maximum waiting time for receive func
	awaitDuration = time.Second * 5
	// // maximum number of messages received at one time
	// maxMessageNum int32 = 16

	// invisibleDuration should > 20s
	invisibleDuration = time.Second * 20

	mqEndpoint  = ""
	mqAccessKey = ""
	mqSecretKey = ""
	mqNameSpace = ""
)

type MqSimpleConsumer struct {
	simpleConsumer mq.SimpleConsumer
	semaphore      chan struct{}
	group          string
	noMsgCnt       int
	once           sync.Once

	ctx    context.Context
	cancel context.CancelFunc
}

type MqPushConsumer struct {
	pushConsumer mq.PushConsumer
	group        string
}

func NewConsumer(typ string) (ConsumerInterface, error) {
	switch typ {
	case SIMPLE:
		return &MqSimpleConsumer{}, nil
	case PUSH:
		return &MqPushConsumer{}, nil
	default:
		return nil, fmt.Errorf("unsupported type")
	}
}

func (ms *MqSimpleConsumer) Start() error {
	os.Setenv("mq.consoleAppender.enabled", "true")
	mq.ResetLogger()

	err := checkMqCfg()
	if err != nil {
		log.WarnF("MqSimpleConsumer,checkMqCfg,err=%v", err)
		return err
	}

	customerCnt := customerCount()
	ms.simpleConsumer, err = mq.NewSimpleConsumer(&mq.Config{
		Endpoint:      mqEndpoint,
		ConsumerGroup: conf.App.RocketMsg.MergeGroup,
		Credentials: &credentials.SessionCredentials{
			AccessKey:    mqAccessKey,
			AccessSecret: mqSecretKey,
		},
	},
		mq.WithSimpleAwaitDuration(awaitDuration),
		mq.WithSimpleSubscriptionExpressions(map[string]*mq.FilterExpression{
			conf.App.RocketMsg.MergeTopic: mq.SUB_ALL,
		}),
	)

	if err != nil {
		log.WarnF("MqSimpleConsumer,New,err=%v", err)
		return err
	}

	// start simpleConsumer
	err = ms.simpleConsumer.Start()
	if err != nil {
		log.WarnF("MqSimpleConsumer,Start,err=%v", err)
		return err
	}

	ms.semaphore = make(chan struct{}, customerCnt)
	ms.noMsgCnt = 0

	// 使用可取消 ctx 驱动消费循环，便于优雅退出时干净停止而非依赖 panic 兜底
	ms.ctx, ms.cancel = context.WithCancel(context.Background())

	log.InfoF("simpleConsumer.Receive start")
	go func() {
		defer func() {
			if v := recover(); v != nil {
				log.WarnK("1008611", "Recovered from MqSimpleConsumer run,err=%v", v)
			}
		}()

		errCnt := 0
		for {
			mvs, err := ms.simpleConsumer.Receive(context.TODO(), customerCnt, invisibleDuration)
			if err != nil {
				if strings.Contains(err.Error(), "MESSAGE_NOT_FOUND") {
					ms.noMsgCnt++
					if ms.noMsgCnt%5 == 0 {
						log.InfoF("MqSimpleConsumer,MESSAGE_NOT_FOUND")
					}
					continue
				}
				log.InfoF("MqSimpleConsumer,Receive,Return,err=%v", err)
				//40401: "MESSAGE_NOT_FOUND",在protocol/v2/definition_pb.go中
				//		50000: "INTERNAL_ERROR",
				//		50001: "INTERNAL_SERVER_ERROR",
				//		50002: "HA_NOT_AVAILABLE",
				//		50100: "NOT_IMPLEMENTED",
				//		50400: "PROXY_TIMEOUT",
				//		50401: "MASTER_PERSISTENCE_TIMEOUT",
				//		50402: "SLAVE_PERSISTENCE_TIMEOUT",
				//		50500: "UNSUPPORTED",
				//		50501: "VERSION_UNSUPPORTED",
				//		50502: "VERIFY_FIFO_MESSAGE_UNSUPPORTED",

				if strings.Contains(err.Error(), "INTERNAL_ERROR") ||
					strings.Contains(err.Error(), "INTERNAL_SERVER_ERROR") ||
					strings.Contains(err.Error(), "HA_NOT_AVAILABLE") ||
					strings.Contains(err.Error(), "simple consumer is not running") {
					log.WarnF("MqSimpleConsumer,Stop Listening,err=%v", err)
					panic("offline Stop Listening")
				}
				errCnt++
				if errCnt < 20 {
					continue
				}
				log.WarnF("MqSimpleConsumer,return")
				return
			}

			if len(mvs) < 1 {
				time.Sleep(100 * time.Millisecond)
				continue
			}

			// ack message
			for _, mv := range mvs {
				key := getKey(mv)
				log.InfoF("SimpleConsumer,messageId=[%v],body=[%v],deliveryAttempt=[%v],key=[%v]",
					mv.GetMessageId(), string(mv.GetBody()), mv.GetDeliveryAttempt(), key)
				ms.semaphore <- struct{}{}
				// log.InfoK(key, "SimpleConsumer,start go,%v ", mv.GetMessageId())
				go func(m *mq.MessageView) {
					defer func() {
						<-ms.semaphore
						if v := recover(); v != nil {
							log.WarnK(key, "Recovered from MqSimpleConsumer run,err=%v", v)
						}
					}()

					log.InfoK(key, "SimpleConsumer,messageId=[%v],start handleMessage", mv.GetMessageId())
					// 处理消息业务逻辑
					sId, handlerErr := handleMessage(key, m)
					if len(key) < 5 {
						key = sId
					}
					if handlerErr == nil {
						log.InfoK(key, "SimpleConsumer,ACK,messageId=[%v]", mv.GetMessageId())
						ms.simpleConsumer.Ack(ms.ctx, mv)
						//ms.simpleConsumer.Ack(context.TODO(), mv)
					} else {
						log.InfoK(key, "SimpleConsumer,NO ACK,messageId=[%v]", mv.GetMessageId())
					}
				}(mv)
			}
		}
	}()

	return nil
}

func (ms *MqSimpleConsumer) GracefulStop() error {
	// 先取消 ctx 让消费循环与 Ack 退出，再停止底层 SDK 消费者
	if ms.cancel != nil {
		ms.cancel()
	}

	if ms.simpleConsumer != nil {
		return ms.simpleConsumer.GracefulStop()
	}
	if ms.semaphore != nil {
		ms.once.Do(func() {
			close(ms.semaphore)
		})
	}
	return nil
}

func (p *MqPushConsumer) Start() error {
	os.Setenv("mq.consoleAppender.enabled", "true")
	mq.ResetLogger()

	err := checkMqCfg()
	if err != nil {
		log.WarnF("MqPushConsumer,checkMqCfg,err=%v", err)
		return err
	}

	consumptionThreadCount := customerCount()

	// In most case, you don't need to create many consumers, singleton pattern is more recommended.
	p.pushConsumer, err = mq.NewPushConsumer(&mq.Config{
		Endpoint:      mqEndpoint,
		ConsumerGroup: conf.App.RocketMsg.MergeGroup,
		Credentials: &credentials.SessionCredentials{
			AccessKey:    mqAccessKey,
			AccessSecret: mqSecretKey,
		},
		NameSpace: mqNameSpace,
	},
		mq.WithPushAwaitDuration(awaitDuration),
		mq.WithPushSubscriptionExpressions(map[string]*mq.FilterExpression{
			conf.App.RocketMsg.MergeTopic: mq.SUB_ALL,
		}),

		mq.WithPushMessageListener(&mq.FuncMessageListener{
			Consume: func(mv *mq.MessageView) mq.ConsumerResult {
				key := getKey(mv)
				log.InfoK(key, "MqPushConsumer,messageId=[%v],body=[%v],deliveryAttempt=[%v],key=[%v]",
					mv.GetMessageId(), string(mv.GetBody()), mv.GetDeliveryAttempt(), key)
				sId, handlerErr := handleMessage(key, mv)
				if len(key) < 5 {
					key = sId
				}
				if handlerErr == nil {
					log.InfoK(key, "MqPushConsumer,SUCCESS,messageId=[%v]", mv.GetMessageId())
					return mq.SUCCESS
				}
				log.InfoK(key, "MqPushConsumer,FAILURE,messageId=[%v]", mv.GetMessageId())
				return mq.FAILURE
			},
		}),
		mq.WithPushConsumptionThreadCount(consumptionThreadCount),
		mq.WithPushMaxCacheMessageCount(1024),
	)

	if err != nil {
		log.WarnF("MqPushConsumer,New,err=%v", err)
		return err
	}

	// start pushConsumer
	err = p.pushConsumer.Start()
	if err != nil {
		log.WarnF("MqPushConsumer,Start,err=%v", err)
		return err
	}
	return nil
}

func (p *MqPushConsumer) GracefulStop() error {
	if p.pushConsumer != nil {
		log.WarnF("MqPushConsumer,GracefulStop")
		return p.pushConsumer.GracefulStop()
	}
	log.WarnF("pushConsumer nil")
	return nil
}

func Init(namespace, accessKey, secretKey, endpoint string) {
	mqEndpoint = endpoint
	mqAccessKey = accessKey
	mqSecretKey = secretKey
	mqNameSpace = namespace
}

func checkMqCfg() error {
	if len(mqEndpoint) < 1 {
		return errors.New("mqEndpoint empty")
	}

	if len(mqAccessKey) < 1 {
		return errors.New("mqAccessKey empty")
	}

	if len(mqSecretKey) < 1 {
		return errors.New("mqSecretKey empty")
	}
	return nil
}

func getKey(mv *mq.MessageView) string {
	key := ""
	if len(mv.GetKeys()) > 1 {
		key = mv.GetKeys()[0]
	}
	return key
}

func customerCount() int32 {
	customerCnt := conf.App.ConsumptionThreadCount
	if customerCnt <= 0 {
		customerCnt = 10
	}
	return customerCnt
}

func handleMessage(sId string, mv *mq.MessageView) (string, error) {
	metrics.MqReceiverInc(metrics.DefaultMode)

	ffMerge := processor.NewFfMerge(sId)
	startMills := time.Now()
	sessionId, mode, err := ffMerge.MergeSh(string(mv.GetBody()), conf.App.RocketMsg.ModeBiz)
	if err != nil {
		log.WarnK(sessionId, "Mq,handleMessage,err=%v", err)
		metrics.MergeFailInc(mode)
	}

	// 确保上游传过来非空，还可以从内部获取
	if len(sId) < 10 {
		sId = sessionId
	}
	sp := time.Since(startMills).Milliseconds()
	metrics.MergeTimeObserve(mode, float64(sp))
	log.InfoK(sId, "Mq,handleMessage,sp=%v", sp)
	if errors.Is(err, models.MqMessageInvalid) {
		log.InfoK(sId, "handleMessage,err is MqMessageInvalid, return nil")
		return sId, nil
	}
	return sId, err
}
