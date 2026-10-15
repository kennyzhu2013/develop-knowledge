package broker

import (
	"offline-merge/internal/pkg/broker/pub"
	"offline-merge/internal/pkg/broker/receive"
)

func Init(namespace, viTopic, dccTopic, accessKey, secretKey, endpoint string, maxAttempts int, modeBiz string) error {
	err := pub.InitProducer(namespace, viTopic, dccTopic, accessKey, secretKey, endpoint, maxAttempts, modeBiz)
	if err != nil {
		return err
	}

	receive.Init(namespace, accessKey, secretKey, endpoint)
	return nil
}

func StopProducer() error {
	err := pub.StopProducer()
	if err != nil {
		return err
	}
	return nil
}
