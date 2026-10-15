package mediasource

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"encoding/json"
	"fmt"
	"sync"
	"vi-media/internal/pkg/log"
	"vi-media/pkg/concurrent/promise"
)

type notice struct {
	// "FRAME"，"CALLING"，"CODEC_SWITCH"，"END"
	Event Event `json:"event"`
	// 数据
	Data interface{} `json:"data"`
}

func (n notice) String() string {
	return fmt.Sprintf("Event: %v, Data: %v", n.Event, n.Data)
}

type Inbound struct {
	source string
	opts   InboundOptions

	conn *Conn
	// 通知队列
	nts chan *notice

	// 退出信号通道
	exit chan struct{}
	// 关闭句柄
	sync.Once
}

func DialInbound(source string, dOpt []DialOption, opt ...InboundOption) promise.Promise {
	inbound := NewInbound(source, dOpt, opt...)
	return promise.New(inbound.opts.DialOpts.ASynchronizer, func() (interface{}, error) {
		inbound.opts.DialOpts.Callback = func(data interface{}, err error) {
			_ = inbound.Close()
			if inbound.opts.Callback != nil {
				inbound.opts.Callback(data, err)
			}
		}
		onSuccess := inbound.opts.DialOpts.OnSuccess
		inbound.opts.DialOpts.OnSuccess = func(conn *Conn) error {
			if onSuccess != nil {
				if err := onSuccess(conn); err != nil {
					return err
				}
			}
			inbound.conn = conn
			if inbound.opts.OnSuccess != nil {
				err := inbound.opts.OnSuccess(inbound)
				if err != nil {
					_ = inbound.conn.Close()
					return err
				}
			}
			return nil
		}
		_, err := DialSync(source, &inbound.opts.DialOpts)
		if err != nil {
			return nil, err
		}
		go inbound.process()
		return inbound, nil
	})
}

func NewInbound(source string, dOpt []DialOption, opt ...InboundOption) *Inbound {
	opts := new(InboundOptions)
	for _, o := range opt {
		o(opts)
	}
	dOpts := new(DialOptions)
	for _, o := range dOpt {
		o(dOpts)
	}
	opts.DialOpts = *dOpts
	return &Inbound{
		source: source,
		opts:   *opts,
		nts:    make(chan *notice, 2),
		exit:   make(chan struct{}),
	}
}

func (o *Inbound) Notice(event Event, data interface{}) {
	select {
	case <-o.exit:
		return
	default:
		select {
		case o.nts <- &notice{Event: event, Data: data}:
		default:
		}
	}
}

func (o *Inbound) process() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(o.opts.DialOpts.Id, "Recovered from inboundProcess with %v, stack: %v", v, process.Stack())
		}
	}()
	for {
		select {
		case <-o.exit:
			log.InfoK(o.opts.DialOpts.Id, "<<Inbound-Process exit!!!>>")
			if o.conn != nil {
				_ = o.conn.Close()
			}
			return
		case nt := <-o.nts:
			data, _ := json.Marshal(nt)
			_, err := o.conn.Write(MessageTypeText, data)
			if err != nil {
				_ = o.Close()
			}
			log.DebugK(o.opts.DialOpts.Id, "Inbound send notice with {%v}, err: %v", nt, err)
		}
	}
}

func (o *Inbound) Close() error {
	o.Do(func() {
		close(o.exit)
	})
	return nil
}