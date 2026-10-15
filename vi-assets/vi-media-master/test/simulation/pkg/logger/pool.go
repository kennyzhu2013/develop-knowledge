package logger

import (
	"io"
	"os"
	"time"
)

type Handler interface {
	io.Writer
}

type Discard struct{}

func (d Discard) Write(_ []byte) (int, error) {
	return 0, nil
}

type Stdout struct{}

func (s Stdout) Write(p []byte) (int, error) {
	return os.Stdout.Write(p)
}

type HandlerFunc func([]byte) (int, error)

func (fn HandlerFunc) Write(p []byte) (n int, err error) {
	return fn(p)
}

type Pool struct {
	// Buffer pool queue, buffer asynchronous log events
	queue chan *Event
	// Buffer size, save log information to be refreshed
	buffer []byte
	// The offset indicates the position of the writeIndex buffer for pending written
	offset int
	// Enqueue timeout duration
	timeout *time.Timer
	// Log processor, flushes the buffer when it is full
	handler Handler
	// Exit signal channel
	exit chan struct{}
}

func newPool(poolSize, bufferSize int, handler Handler) *Pool {
	p := new(Pool)
	p.queue = make(chan *Event, poolSize)
	p.buffer = make([]byte, bufferSize)
	p.timeout = time.NewTimer(writeTimeout)
	p.exit = make(chan struct{})
	p.handler = handler
	if p.handler == nil {
		p.handler = Stdout{}
	}
	go p.run()
	return p
}

func (p *Pool) Put(event *Event, timeout time.Duration) error {
	p.timeout.Reset(timeout)
	select {
	case <-p.exit:
		return ErrExit
	case p.queue <- event:
		return nil
	case <-p.timeout.C:
		return ErrEnqueueTimeout
	}
}

func (p *Pool) Write(msg []byte) (int, error) {
	if p.offset+len(msg) > len(p.buffer) {
		p.flush()
	}
	p.buffer = append(p.buffer[:p.offset], msg...)
	p.offset += len(msg)
	return len(msg), nil
}

func (p *Pool) flush() {
	if p.offset <= 0 {
		return
	}
	_, _ = p.handler.Write(p.buffer[:p.offset])
	p.buffer = p.buffer[:0]
	p.offset = 0
}

func (p *Pool) run() {
	ticker := time.NewTicker(flushInterval)
	for {
		select {
		case <-p.exit:
			if p.timeout != nil {
				p.timeout.Stop()
			}
			ticker.Stop()
			ticker = nil
			return
		case <-ticker.C:
			p.flush()
		case e := <-p.queue:
			_, _ = e.Msg(p)
		}
	}
}

func (p *Pool) Close() error {
	close(p.exit)
	return nil
}