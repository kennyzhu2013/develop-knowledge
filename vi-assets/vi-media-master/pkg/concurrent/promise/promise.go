package promise

import (
	"context"
	"errors"
	"io"
	"sync/atomic"
	"time"
)

var ErrTimeout = errors.New("timeout")

type Promise interface {
	io.Closer
	Await() error
	AwaitTimeout(time.Duration) error
	Value() interface{}
	Err() error
}

type noOpPromise struct{}

func Background() Promise {
	return noOpPromise{}
}

func (p noOpPromise) Await() error {
	return nil
}

func (p noOpPromise) AwaitTimeout(_ time.Duration) error {
	return nil
}

func (p noOpPromise) Close() error {
	return nil
}

func (p noOpPromise) Value() interface{} {
	return nil
}

func (p noOpPromise) Err() error {
	return nil
}

type promise struct {
	context    context.Context
	cancelFunc context.CancelFunc

	val atomic.Value
	err atomic.Value

	state uint32
}

func (p *promise) Await() error {
	<-p.context.Done()
	return p.err.Load().(error)
}

func (p *promise) AwaitTimeout(duration time.Duration) error {
	select {
	case <-time.After(duration):
		return ErrTimeout
	case <-p.context.Done():
		return p.Err()
	}
}

func (p *promise) Close() error {
	atomic.StoreUint32(&p.state, 1)
	v, ok := p.val.Load().(io.Closer)
	if ok && v != nil {
		_ = v.Close()
	}
	p.cancelFunc()
	return nil
}

func (p *promise) Value() interface{} {
	return p.val.Load()
}

func (p *promise) Err() error {
	return p.err.Load().(error)
}

func New(async ASynchronizer, fn func() (interface{}, error)) Promise {
	if async == nil {
		async = &defaultASynchronizer{}
	}
	ctx, cancelFunc := context.WithCancel(context.Background())
	p := &promise{context: ctx, cancelFunc: cancelFunc}
	async.Async(func() {
		defer p.cancelFunc()
		val, err := fn()
		if err != nil {
			p.err.Store(err)
		} else {
			p.val.Store(val)
		}
		if atomic.LoadUint32(&p.state) == 1 {
			v, ok := val.(io.Closer)
			if ok && v != nil {
				_ = v.Close()
			}
		}
	})
	return p
}