package pub

import (
	"offline-merge/internal/models"

	mq "github.com/apache/rocketmq-clients/golang/v5"
	"github.com/apache/rocketmq-clients/golang/v5/credentials"
)

var (
	namespace   string
	accessKey   string
	secretKey   string
	endpoint    string
	maxAttempts int

	viTopic  string
	dccTopic string

	viProducer  *ViProducer
	dccProducer *DccProducer
)

type ProducerInterface interface {
	InitProducer() error
	PubBillMessage(sId string, data interface{}) error
	StopProducer() error
}

func GetProducer(mode string) ProducerInterface {
	if mode == models.BizVi {
		if viProducer == nil {
			viProducer = NewViProducer(viTopic)
		}
		return viProducer
	} else {
		if dccProducer == nil {
			dccProducer = NewDccProducer(dccTopic)
		}
		return dccProducer
	}
}

func InitProducer(nSpace, vTopic, dTopic, ak, sk, ePoint string, maxAttempt int, modeBiz string) error {
	namespace = nSpace
	accessKey = ak
	secretKey = sk
	endpoint = ePoint
	maxAttempts = maxAttempt

	viTopic = vTopic
	dccTopic = dTopic

	viProducer = NewViProducer(viTopic)
	dccProducer = NewDccProducer(dccTopic)

	if modeBiz == models.BizVi {
		viProducer.InitProducer()
	} else {
		dccProducer.InitProducer()
	}

	return nil
}

func StopProducer() error {
	var err error
	if viProducer != nil {
		err = viProducer.StopProducer()
	}
	if dccProducer != nil {
		err = dccProducer.StopProducer()
	}
	return err
}

func clientProducer(topic string) (mq.Producer, error) {
	producer, err := mq.NewProducer(&mq.Config{
		Endpoint:  endpoint,
		NameSpace: namespace,
		Credentials: &credentials.SessionCredentials{
			AccessKey:    accessKey,
			AccessSecret: secretKey,
		},
	}, mq.WithMaxAttempts(int32(maxAttempts)), mq.WithTopics(topic))
	if err != nil {
		return nil, err
	}
	err = producer.Start()
	if err != nil {
		return nil, err
	}
	return producer, nil
}
