package notify

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
	"vi-merge/internal/pkg/log"
)

var (
	globalClient *HttpNotify

	// 任务通道 + 常驻消费协程
	taskChan chan SendParam
	wg       sync.WaitGroup
)

type HttpNotify struct {
	sId    string
	cfg    *Config
	client *http.Client
}

type SendParam struct {
	Url     string
	Body    interface{}
	Headers map[string]string
}

func GetClient() *HttpNotify {
	if globalClient == nil {
		dCfg := DefaultConfig()
		globalClient = NewNotifyClient(dCfg)
		initWorker(dCfg)
	}
	return globalClient
}

func Init(cfg *Config) {
	if globalClient == nil {
		globalClient = NewNotifyClient(cfg)
		initWorker(cfg)
	}
}

func initWorker(cfg *Config) {
	taskChan = make(chan SendParam, cfg.ChannelCapacity)
	for i := 0; i < cfg.WorkerNum; i++ {
		wg.Add(1)
		go func() {
			log.InfoK("10086", "Notify InitWorker Start")
			defer func() {
				wg.Done()
				if v := recover(); v != nil {
					log.WarnK("10086", "Notify Async panic ,v=%v", v)
				}
			}()

			for param := range taskChan {
				err := globalClient.SendSync(param)
				if err != nil {
					log.WarnK("10086", "[Notify Async Fail] url=%s err=%v\n", param.Url, err)
				}
			}
		}()
	}
}

func NotifyClose() {
	if taskChan != nil {
		close(taskChan)
		wg.Wait()
	}

}

func NewNotifyClient(cfg *Config) *HttpNotify {
	return &HttpNotify{
		cfg: cfg,
		client: &http.Client{
			Transport: &http.Transport{
				TLSHandshakeTimeout:   cfg.Timeout * time.Second,
				ResponseHeaderTimeout: cfg.Timeout * time.Second,
			},
			Timeout: cfg.Timeout * time.Second},
	}
}

func (c *HttpNotify) SendSync(param SendParam) error {
	bodyBytes, err := json.Marshal(param.Body)
	if err != nil {
		return fmt.Errorf("HttpNotify,marshal fail: %v", err)
	}

	headers := make(map[string]string)
	for k, v := range c.cfg.DefaultHeaders {
		headers[k] = v
	}
	for k, v := range param.Headers {
		headers[k] = v
	}

	for i := 1; i <= c.cfg.RetryTimes; i++ {
		err = c.request(param, bodyBytes, headers)
		if err == nil {
			break
		}
		if i >= c.cfg.RetryTimes {
			break
		}
		time.Sleep(c.cfg.RetryDelay)
	}
	return err
}

func (c *HttpNotify) request(param SendParam, bodyBytes []byte, headers map[string]string) error {
	req, err := http.NewRequest(http.MethodPost, param.Url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return fmt.Errorf("HttpNotify,create request fail: %w", err)
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}

	var resp *http.Response
	resp, err = c.client.Do(req)
	if err != nil {
		return fmt.Errorf("HttpNotify,fail: %v", err)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("HttpNotify,Body Fail: %v", err)
	}
	defer resp.Body.Close()

	log.InfoK(c.sId, "HttpNotify,resp=%v;code=%v", string(body), resp.StatusCode)
	statusCode := resp.StatusCode / 100
	if statusCode == 2 {
		return nil
	}
	return fmt.Errorf("StatusCode not ok,v=%v", resp.StatusCode)
}

func (c *HttpNotify) SendAsync(param SendParam) {
	select {
	case taskChan <- param:
	default:
		// 队列已满，防止阻塞业务主线程
		log.WarnK("1008611", "Notify task channel full, discard url:%v ,paramBody=%v", param.Url, param.Body)
	}
}

func (c *HttpNotify) SendAsyncGo(param SendParam) {
	go func() {
		defer func() {
			if v := recover(); v != nil {
				log.WarnK(c.sId, "Notify Async panic ,v=%v", v)
			}
		}()

		err := c.SendSync(param)
		if err != nil {
			log.WarnK(c.sId, "Notify Async fail,url=%s,err=%v", param.Url, err)
		}
	}()
}

func SendAsync(sId string, url string, body interface{}, customHeader ...map[string]string) {
	header := make(map[string]string)
	if len(customHeader) > 0 {
		header = customHeader[0]
	}
	httpNotifyClient := GetClient()
	httpNotifyClient.sId = sId
	httpNotifyClient.SendAsync(SendParam{
		Url:     url,
		Body:    body,
		Headers: header,
	})
}

func SendSync(sId string, url string, body interface{}, customHeader ...map[string]string) error {
	header := make(map[string]string)
	if len(customHeader) > 0 {
		header = customHeader[0]
	}
	httpNotifyClient := GetClient()
	httpNotifyClient.sId = sId
	return httpNotifyClient.SendSync(SendParam{
		Url:     url,
		Body:    body,
		Headers: header,
	})
}
