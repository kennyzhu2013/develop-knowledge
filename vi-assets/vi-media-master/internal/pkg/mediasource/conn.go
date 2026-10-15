package mediasource

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	cReg "cmic.chinamobile.com/ziyan/go-sdk/registry"
	"context"
	"errors"
	"fmt"
	"github.com/gorilla/websocket"
	"net/http"
	"strings"
	"sync"
	"time"
	"vi-media/internal/pkg/kv/registry"
	"vi-media/internal/pkg/log"
	"vi-media/pkg/concurrent/promise"
)

type WriteStream func(int, []byte) (interface{}, error)

type Conn struct {
	conn *websocket.Conn
	opts *DialOptions
	sync.Once
}

func Dial(source string, opt ...DialOption) promise.Promise {
	opts := new(DialOptions)
	for _, o := range opt {
		o(opts)
	}
	return DialOpts(source, opts)
}

func DialOpts(source string, opts *DialOptions) promise.Promise {
	return promise.New(opts.ASynchronizer, func() (interface{}, error) {
		conn, err := DialSync(source, opts)
		if err != nil {
			return nil, err
		}
		return conn, err
	})
}

func DialSync(source string, opts *DialOptions) (*Conn, error) {
	url, err := getResource(source)
	if err != nil {
		if opts.Callback != nil {
			opts.Callback(nil, err)
		}
		return nil, err
	}
	dialer := &websocket.Dialer{Proxy: http.ProxyFromEnvironment, HandshakeTimeout: opts.HandshakeTimeout}
	remote, _, nErr := dialer.DialContext(context.Background(), url, opts.Header)
	if nErr != nil {
		if opts.Callback != nil {
			opts.Callback(nil, nErr)
		}
		return nil, fmt.Errorf("dial %v failed: %v", source, nErr)
	}
	conn := &Conn{conn: remote, opts: opts}
	if opts.OnSuccess != nil {
		nErr = opts.OnSuccess(conn)
	}
	if nErr != nil {
		_ = remote.WriteControl(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""), time.Now().Add(opts.WriteTimeout))
		_ = remote.Close()
		if opts.Callback != nil {
			opts.Callback(nil, nErr)
		}
		return nil, nErr
	}
	// Enable read
	if opts.ReadEnabled {
		if opts.ASynchronizer == nil {
			go conn.readLoop()
		} else {
			opts.ASynchronizer.Async(conn.readLoop)
		}
	}
	return conn, nil
}

func (c *Conn) readLoop() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(c.opts.Id, "Recovered from mediaSource readLoop with %v, stack: %v", v, process.Stack())
		}
	}()
	var err error
	var msg []byte
	var msgType int
	var resp interface{}
	for {
		msgType, msg, err = c.conn.ReadMessage()
		if err != nil {
			if strings.HasSuffix(err.Error(), "use of closed network connection") || websocket.IsCloseError(err, websocket.CloseNormalClosure,
				websocket.CloseGoingAway, websocket.CloseNoStatusReceived) {
				err = nil
			}
			break
		}
		if c.opts.WriteStream != nil {
			resp, err = c.opts.WriteStream(msgType, msg)
			if err != nil {
				break
			}
		}
	}
	if c.opts.Callback != nil {
		if iErr, _ := c.opts.err.Load().(error); iErr != nil {
			err = iErr
		}
		c.opts.Callback(resp, err)
	}
}

func (c *Conn) Write(mType int, p []byte) (int, error) {
	if c == nil || c.conn == nil || len(p) == 0 {
		return 0, nil
	}
	_ = c.conn.SetWriteDeadline(time.Now().Add(c.opts.WriteTimeout))
	err := c.conn.WriteMessage(mType, p)
	if err != nil {
		return 0, err
	}
	return len(p), nil
}

func (c *Conn) Close() error {
	if c.conn == nil {
		return nil
	}
	c.Do(func() {
		_ = c.conn.WriteControl(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""), time.Now().Add(c.opts.WriteTimeout))
		_ = c.conn.Close()
	})
	return nil
}

func getResource(source string) (string, error) {
	var err error
	source = strings.TrimPrefix(source, ProtocolWS)
	serverName, destination := source, source
	idx := strings.Index(source, "/")
	if idx != -1 {
		serverName = source[:idx]
		destination = serverName
	}
	if strings.HasPrefix(serverName, ":") {
		destination, err = getNode(serverName[1:])
		if err != nil {
			return "", err
		}
	}
	return ProtocolWS + strings.Replace(source, serverName, destination, 1), nil
}

var index = -1
var mu sync.Mutex

func getNode(name string) (string, error) {
	var err error
	var instance *cReg.Instance
	idx := strings.Index(name, "@")
	if idx == -1 {
		instance, err = registry.GetInstance(name)
	} else {
		instance, err = registry.GetInstanceWithCluster(name[:idx], name[idx+1:])
	}
	if err != nil || len(instance.Nodes) == 0 {
		return "", errors.New(fmt.Sprintf("could not find %v app from registry: %v", name, err))
	}
	mu.Lock()
	defer mu.Unlock()
	index++
	if index >= len(instance.Nodes) {
		index = 0
	}
	return fmt.Sprintf("%v:%v", instance.Nodes[index].Host, instance.Nodes[index].Port), nil
}