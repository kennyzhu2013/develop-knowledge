package broker

import (
	"offline-merge/internal/pkg/broker/receive"
	"offline-merge/internal/pkg/log"
)

var consumerImp receive.ConsumerInterface

func StartConsumer() {
	if consumerImp != nil {
		log.SysError("Broker NewConsumer running...;return")
		return
	}
	ConsumerInterface, err := receive.NewConsumer(receive.PUSH)
	if err != nil {
		log.SysError("Broker NewConsumer failed: %v", err)
		panic(err.Error())
	}
	consumerImp = ConsumerInterface
	err = ConsumerInterface.Start()
	if err != nil {
		log.SysError("Broker NewConsumer Start failed: %v", err)
		panic(err.Error())
	}
	log.SysError("Broker NewConsumer started")
}

func MqGracefulStop() {
	//mq消费者关闭
	if consumerImp != nil {
		log.InfoF("consumer Stopped,Please wait,it will take a few seconds. Upon success, the success log will be printed")
		err := consumerImp.GracefulStop()
		if err != nil {
			log.SysError("consumer Broker Stop failed: %v", err)
		} else {
			log.InfoF("consumer Broker Stopped Successfully")
			consumerImp = nil
		}
	} else {
		log.InfoF("consumer is nil")
	}
}
