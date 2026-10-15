package internal

import (
	"bytes"
	"context"
	"crypto/tls"
	"github.com/bytedance/sonic"
	"io"
	"net/http"
	"time"
)

var client = &http.Client{
	Transport: &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: true,
		},
		MaxIdleConns:        50,
		IdleConnTimeout:     60 * time.Second,
		TLSHandshakeTimeout: 10 * time.Second,
	},
	Timeout: 15 * time.Second,
}

func post(url string, params interface{}, header http.Header) (int, []byte, error) {
	return postWithContext(context.Background(), url, params, header)
}

func postWithContext(ctx context.Context, url string, params interface{}, header http.Header) (int, []byte, error) {
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
	request.Header = header
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