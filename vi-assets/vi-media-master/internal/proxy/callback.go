package proxy

import (
	"bytes"
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"context"
	"crypto/tls"
	"fmt"
	"github.com/bytedance/sonic"
	"github.com/sony/gobreaker"
	"golang.org/x/sync/singleflight"
	"io"
	"net/http"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/log"
)

var sg singleflight.Group

var circuitBreaker *gobreaker.CircuitBreaker

var client = &http.Client{
	Transport: &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: true,
		},
		MaxIdleConns:        50,
		IdleConnTimeout:     60 * time.Second,
		TLSHandshakeTimeout: 10 * time.Second,
	},
	Timeout: 10 * time.Second,
}

func initCircuitBreaker() {
	circuitBreaker = gobreaker.NewCircuitBreaker(gobreaker.Settings{
		Name:        "cb-cdn",
		MaxRequests: conf.App.Cache.CircuitBreaker.MaxRequests,
		Interval:    time.Duration(conf.App.Cache.CircuitBreaker.Interval) * time.Second,
		Timeout:     time.Duration(conf.App.Cache.CircuitBreaker.Timeout) * time.Second,
		ReadyToTrip: func(counts gobreaker.Counts) bool {
			failureRatio := float64(counts.TotalFailures) / float64(counts.Requests)
			return counts.ConsecutiveFailures > conf.App.Cache.CircuitBreaker.ConsecutiveFailures || (counts.Requests > conf.App.Cache.CircuitBreaker.ConsecutiveFailures &&
				failureRatio >= 0.6)
		},
		OnStateChange: func(name string, from gobreaker.State, to gobreaker.State) {
			log.InfoF("<<CircuitBreaker change state!!!>> - from %v to %v", from, to)
		},
	})
}

func post(url string, params interface{}) (int, []byte, error) {
	return postWithContext(context.Background(), url, params)
}

func postWithContext(ctx context.Context, url string, params interface{}) (int, []byte, error) {
	var reader io.Reader
	if params != nil {
		data, err := sonic.Marshal(params)
		if err != nil {
			return 0, nil, err
		}
		reader = bytes.NewReader(data)
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, url, reader)
	if err != nil {
		return 0, nil, err
	}
	request.Header.Set("Content-Type", "application/json;charset=UTF-8")
	resp, err := client.Do(request)
	if err != nil {
		return 0, nil, err
	}
	defer func(Body io.ReadCloser) {
		_ = Body.Close()
	}(resp.Body)
	rData, err := io.ReadAll(resp.Body)
	return resp.StatusCode, rData, err
}

func cbPost(ctx context.Context, url string, params interface{}, key string) (int, []byte, error) {
	result, err := circuitBreaker.Execute(func() (interface{}, error) {
		resp, err, _ := sg.Do(key, func() (interface{}, error) {
			var reader io.Reader
			if params != nil {
				data, err := sonic.Marshal(params)
				if err != nil {
					return nil, err
				}
				reader = bytes.NewReader(data)
			}
			request, err := http.NewRequestWithContext(ctx, http.MethodPost, url, reader)
			if err != nil {
				return nil, err
			}
			request.Header.Set("Content-Type", "application/json;charset=UTF-8")
			return client.Do(request)
		})
		if err != nil {
			return nil, err
		}
		return resp, nil
	})
	if err != nil {
		return 0, nil, err
	}
	resp := result.(*http.Response)
	defer func(Body io.ReadCloser) {
		_ = Body.Close()
	}(resp.Body)
	rData, err := io.ReadAll(resp.Body)
	return resp.StatusCode, rData, err
}

type dtmfResult struct {
	Digit    string `json:"digit"`
	EndCause string `json:"endCause"`
	Source   int    `json:"source"`
}

func (d *dtmfResult) String() string {
	return fmt.Sprintf("{digit: %s, endCause: %s, source: %d}", d.Digit, d.EndCause, d.Source)
}

type eventResult struct {
	SessionId string      `json:"sessionId"`
	Event     string      `json:"event"`
	Code      string      `json:"code"`
	Message   string      `json:"message"`
	Data      interface{} `json:"data"`
	SeqId     uint8       `json:"seqId"`
}

func callback(seqId uint8, sessionId, event, url string, data interface{}, err error) {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(sessionId, "Recovered from session event callback with %v, stack: %v", v, process.Stack())
		}
	}()
	params := &eventResult{SessionId: sessionId, Event: event, SeqId: seqId, Code: "200", Data: data}
	if err != nil {
		params.Code = "500"
		params.Message = err.Error()
	}
	statusCode, rData, err := post(url, params)
	if err != nil {
		log.WarnK(sessionId, "Session callback %v for <event: %v, data: %v> failed: %v", url, event, data, err)
		return
	}
	if statusCode != http.StatusOK {
		log.WarnK(sessionId, "Session callback %v for <event: %v, data: %v> failed with %v: %v", url, event, data, statusCode, string(rData))
		return
	}
	log.InfoK(sessionId, "Session callback %v for <event: %v, data: %v> success: %v", url, event, data, string(rData))
}