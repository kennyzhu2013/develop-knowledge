package mediasource

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"encoding/json"
	"fmt"
	"sync"
	"sync/atomic"
	"vi-media/internal/pkg/log"
	"vi-media/pkg/concurrent/promise"
)

type packet struct {
	// 补帧个数
	Frames int
	// 媒体数据
	Data []byte
	// 事件，"FRAME"，"CALLING"，"CODEC_SWITCH"，"END"
	Event Event
	// 事件数据
	EventData interface{}
}

func (p packet) String() string {
	return fmt.Sprintf("Frames: %v, Event: %v, EventData: %v", p.Frames, p.Event, p.EventData)
}

type command struct {
	// The event name
	Event Event
	// THe event message
	Message string
	// The event data
	Data interface{}
}

func (t command) String() string {
	return fmt.Sprintf("Event: %v, Message: %v", t.Event, t.Message)
}

type Outbound struct {
	state  int32
	source string
	opts   OutboundOptions

	conn *Conn
	// 媒体队列
	packets chan *packet

	// 退出信号通道
	exit chan struct{}
	// The sema chan for the notice
	sema chan command
	// 关闭句柄
	sync.Once
}

func DialOutbound(source string, dOpt []DialOption, opt ...OutboundOption) promise.Promise {
	outbound := NewOutbound(source, dOpt, opt...)
	return promise.New(outbound.opts.DialOpts.ASynchronizer, func() (interface{}, error) {
		outbound.opts.DialOpts.Callback = func(data interface{}, err error) {
			_ = outbound.Close()
			if outbound.opts.Callback != nil {
				outbound.opts.Callback(data, err)
			}
		}
		onSuccess := outbound.opts.DialOpts.OnSuccess
		outbound.opts.DialOpts.OnSuccess = func(conn *Conn) error {
			if onSuccess != nil {
				if err := onSuccess(conn); err != nil {
					return err
				}
			}
			outbound.conn = conn
			if outbound.opts.OnSuccess != nil {
				err := outbound.opts.OnSuccess(outbound)
				if err != nil {
					_ = outbound.conn.Close()
					return err
				}
			}
			return nil
		}
		_, err := DialSync(source, &outbound.opts.DialOpts)
		if err != nil {
			return nil, err
		}
		go outbound.process()
		atomic.StoreInt32(&outbound.state, StateRunning)
		return outbound, nil
	})
}

func NewOutbound(source string, dOpt []DialOption, opt ...OutboundOption) *Outbound {
	opts := new(OutboundOptions)
	for _, o := range opt {
		o(opts)
	}
	dOpts := new(DialOptions)
	for _, o := range dOpt {
		o(dOpts)
	}
	opts.DialOpts = *dOpts
	return &Outbound{
		state:   StateNew,
		source:  source,
		opts:    *opts,
		packets: make(chan *packet, opts.QSize),
		exit:    make(chan struct{}),
		sema:    make(chan command, 1),
	}
}

func (o *Outbound) Startup() error {
	if atomic.CompareAndSwapInt32(&o.state, StateNew, StatePrepare) {
		o.opts.DialOpts.Callback = func(data interface{}, err error) {
			_ = o.Close()
			if o.opts.Callback != nil {
				o.opts.Callback(data, err)
			}
		}
		onSuccess := o.opts.DialOpts.OnSuccess
		o.opts.DialOpts.OnSuccess = func(conn *Conn) error {
			if onSuccess != nil {
				if err := onSuccess(conn); err != nil {
					return err
				}
			}
			o.conn = conn
			return nil
		}
		_, err := DialSync(o.source, &o.opts.DialOpts)
		if err != nil {
			atomic.StoreInt32(&o.state, StateNew)
			return err
		}
		if o.opts.OnSuccess != nil {
			if err = o.opts.OnSuccess(o); err != nil {
				_ = o.conn.Close()
				return err
			}
		}
		go o.process()
		atomic.StoreInt32(&o.state, StateRunning)
	}
	return nil
}

func (o *Outbound) Send(kind, source byte, frames int, data []byte) {
	select {
	case <-o.exit:
		return
	default:
		dataCopy := make([]byte, len(data)+2)
		dataCopy[0] = kind
		dataCopy[1] = source
		copy(dataCopy[2:], data)
		select {
		case o.packets <- &packet{Event: EventData, Frames: frames, Data: dataCopy}:
		default:
		}
	}
}

func (o *Outbound) Notice(event Event, data interface{}) {
	select {
	case <-o.exit:
		return
	default:
		select {
		case o.packets <- &packet{Event: event, EventData: data}:
		default:
		}
	}
}

func (o *Outbound) Sema(event Event, data interface{}, message string) {
	select {
	case <-o.exit:
		return
	default:
		select {
		case o.sema <- command{Event: event, Message: message, Data: data}:
		default:
		}
	}
}

func (o *Outbound) process() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(o.opts.DialOpts.Id, "Recovered from outboundProcess with %v, stack: %v", v, process.Stack())
		}
	}()
	for {
		select {
		case <-o.exit:
			log.InfoK(o.opts.DialOpts.Id, "<<Outbound-Process exit!!!>>")
			o.state = StateTerminated
			if o.conn != nil {
				data, _ := json.Marshal(notice{Event: EventEnd})
				_, err := o.conn.Write(MessageTypeText, data)
				log.DebugK(o.opts.DialOpts.Id, "Outbound send eof message with %v", err)
				_ = o.conn.Close()
			}
			if !o.opts.DialOpts.ReadEnabled {
				err, _ := o.opts.DialOpts.err.Load().(error)
				o.opts.DialOpts.Callback(nil, err)
			}
			return
		case cmd := <-o.sema:
			log.InfoK(o.opts.DialOpts.Id, "<<Outbound-Process exit!!!>> -- <%v>", cmd)
			o.state = StateTerminated
			if o.conn != nil {
				data, _ := json.Marshal(notice{Event: EventEnd})
				_, err := o.conn.Write(MessageTypeText, data)
				log.DebugK(o.opts.DialOpts.Id, "Outbound send eof message with %v", err)
				_ = o.conn.Close()
			}
			if !o.opts.DialOpts.ReadEnabled {
				o.opts.DialOpts.Callback(cmd.Data, fmt.Errorf("disconnect due to exception with %v", cmd.Message))
			}
			return
		case pp := <-o.packets:
			// Data
			if pp.Event == EventData {
				if pp.Frames > 0 {
					data, _ := json.Marshal(notice{Event: EventFrame, Data: pp.Frames})
					if _, err := o.conn.Write(MessageTypeText, data); err != nil {
						o.opts.DialOpts.err.Store(err)
						_ = o.Close()
						continue
					}
					log.DebugK(o.opts.DialOpts.Id, "Outbound send frame interpolation notice")
				}
				_, err := o.conn.Write(MessageTypeBinary, pp.Data)
				if err == nil {
					log.DebugK(o.opts.DialOpts.Id, "Outbound send message: %v", len(pp.Data))
					continue
				}
				o.opts.DialOpts.err.Store(err)
				_ = o.Close()
			}
			// Notice
			data, _ := json.Marshal(notice{Event: pp.Event, Data: pp.EventData})
			_, err := o.conn.Write(MessageTypeText, data)
			if err == nil {
				log.DebugK(o.opts.DialOpts.Id, "Outbound send notice with {%v}", pp)
				continue
			}
			o.opts.DialOpts.err.Store(err)
			_ = o.Close()
		}
	}
}

func (o *Outbound) Close() error {
	o.Do(func() {
		close(o.exit)
	})
	return nil
}