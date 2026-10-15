package tts

import (
	"bytes"
	"crypto/tls"
	"errors"
	"fmt"
	"github.com/bytedance/sonic"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"
	"vi-media/internal/pkg/kv/registry"
	"vi-media/pkg/rtp/codec"
)

const ProtocolHTTP = "http://"

func getResource(source string) (string, error) {
	var err error
	source = strings.TrimPrefix(source, ProtocolHTTP)
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
	return ProtocolHTTP + strings.Replace(source, serverName, destination, 1), nil
}

var index = -1
var mu sync.Mutex

func getNode(appName string) (string, error) {
	instance, err := registry.GetInstance(appName)
	if err != nil || len(instance.Nodes) == 0 {
		return "", errors.New(fmt.Sprintf("could not find %v app from registry: %v", appName, err))
	}
	mu.Lock()
	defer mu.Unlock()
	index++
	if index >= len(instance.Nodes) {
		index = 0
	}
	return fmt.Sprintf("%v:%v", instance.Nodes[index].Host, instance.Nodes[index].Port), nil
}

var client = &http.Client{
	Transport: &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: true,
		},
		MaxIdleConns:        50,
		IdleConnTimeout:     60 * time.Second,
		TLSHandshakeTimeout: 10 * time.Second,
	},
	Timeout: 5 * time.Second,
}

func post(url string, params interface{}) (int, []byte, error) {
	data, err := sonic.Marshal(params)
	if err != nil {
		return 0, nil, err
	}
	request, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(data))
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

type param struct {
	SessionId string `json:"sessionId"`
	MimeType  string `json:"mimeType"`
	Text      string `json:"text"`
}

func Get(id, source, content, mimeType string) ([][]byte, error) {
	url, err := getResource(source)
	if err != nil {
		return nil, err
	}
	code, data, err := post(url, param{SessionId: id, MimeType: mimeType, Text: content})
	if code != http.StatusOK || err != nil {
		return nil, fmt.Errorf("tts failed, code: %v, err: %v", code, err)
	}
	if len(data) == 0 {
		return nil, errors.New("tts failed, no data")
	}
	fg := codec.Sharding(mimeType, data)
	if fg == nil {
		return nil, fmt.Errorf("tts failed, %v, mimeType: %v", codec.ErrUnknownType, mimeType)
	}
	return fg[0].Frames, nil
}