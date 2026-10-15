package engine

import (
	"context"
	"os/signal"
	"runtime"
	"strconv"
	"sync"
	"syscall"
)

type HandlerFunc func(*Context)
type HandlerChain []HandlerFunc

type Engine struct {
	*Snowflake
	*PortPool

	config Configuration
	rootCh HandlerChain

	sync.Pool
	wg   sync.WaitGroup
	root context.Context
}

func New(config Configuration) (*Engine, error) {
	sf, err := NewSnowflake(MachineId, DataCenterId)
	if err != nil {
		return nil, err
	}
	pp, err := NewPortPool(config.MinP, config.MaxG)
	if err != nil {
		return nil, err
	}
	engine := &Engine{Snowflake: sf, PortPool: pp, config: config}
	engine.Pool.New = func() interface{} {
		return engine.allocateContext()
	}
	engine.root = context.Background()
	return engine, nil
}

func (m *Engine) Do() {
	for i := 0; i < m.config.MaxG; i++ {
		m.wg.Add(1)
		go func() {
			defer m.wg.Done()
			ctx := m.Pool.Get().(*Context)
			defer m.Pool.Put(ctx)

			ctx.Reset()
			port := m.PortPool.Get()
			for port == -1 {
				runtime.Gosched()
				port = m.PortPool.Get()
			}
			defer m.PortPool.Put(port)

			ctx.Set(ContextSessionId, "70000000000000"+strconv.FormatInt(m.Snowflake.Next(), 10))
			ctx.Set(ContextPort, port)
			ctx.chain = m.rootCh
			ctx.Next()
		}()
	}
}

func (m *Engine) DoLoop() {
	nCtx, _ := signal.NotifyContext(m.root, syscall.SIGTERM, syscall.SIGINT)
	for i := 0; i < m.config.MaxG; i++ {
		m.wg.Add(1)
		go func() {
			defer m.wg.Done()
			ctx := m.Pool.Get().(*Context)
			defer m.Pool.Put(ctx)

			ctx.Reset()
			port := m.PortPool.Get()
			for port == -1 {
				runtime.Gosched()
				port = m.PortPool.Get()
			}
			defer m.PortPool.Put(port)

			ctx.chain = m.rootCh
			for {
				select {
				case <-nCtx.Done():
					return
				default:
					ctx.Set(ContextPort, port)
					ctx.Set(ContextSessionId, "70000000000000"+strconv.FormatInt(m.Snowflake.Next(), 10))
					ctx.Next()
					ctx.Toggle()
				}
			}
		}()
	}
}

func (m *Engine) Wait() {
	m.wg.Wait()
}

func (m *Engine) Add(fn HandlerFunc) *Engine {
	m.rootCh = append(m.rootCh, fn)
	return m
}

func (m *Engine) allocateContext() *Context {
	ctx, cancel := context.WithCancel(m.root)
	return &Context{index: -1, Context: ctx, cancel: cancel, engine: m}
}