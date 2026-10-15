package httpinterface

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime/multipart"
	"net"
	"net/http"
	"net/url"
	"offline-merge/conf"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"os"
	"sync"
	"time"
)

const (
	HEAD_SESSION = "session"
	HEAD_VOLUME  = "Volume"

	FILE_SUC_CODE      = 0  // 上传成功码
	CONNECT_ERROR_CODE = -1 // 连接错误码
	CONNECT_ERROR_5XX  = -2 // 5xx错误，服务问题
	FILE_FAIL_CODE     = -3 // 上传失败码

	CONNECT_ERROR_READ_TIMEOUT = -11 //读超时
	CONNECT_ERROR_NO_ACCESS    = -14 //不可达
	CONNECT_ERROR_404          = -15 //404异常
	CONNECT_ERROR_SOCKET       = -16 //SocketException

	HTTP_NORMAL = "httpNormal"
	HTTP_LONG   = "httpLong"
	LONG_FILE   = 6 * 1024 * 1024
)

var (
	clientMap sync.Map
)

func NewHttpClient(name string) *http.Client {
	val, ok := clientMap.Load(name)
	if ok {
		return val.(*http.Client)
	}
	transport, timeOut := httpTransport(name)
	client := &http.Client{
		Transport: transport,
		Timeout:   time.Duration(timeOut) * time.Second,
	}
	clientMap.Store(name, client)
	return client
}

func httpTransport(tag string) (*http.Transport, int) {
	timeOut := conf.App.Outbound.Timeout
	maxIdleConns := 300
	maxConnsPerHost := 100
	maxIdleConnsPerHost := 50

	if tag == HTTP_LONG {
		timeOut = conf.App.Outbound.Timeout * 2
		maxIdleConns = 200
		maxConnsPerHost = 50
		maxIdleConnsPerHost = 25
	}
	transport := &http.Transport{
		MaxIdleConns:        maxIdleConns,
		MaxIdleConnsPerHost: maxIdleConnsPerHost,
		MaxConnsPerHost:     maxConnsPerHost,
		// 超时配置，解决TCP僵死、goroutine泄漏
		IdleConnTimeout:       30 * time.Second, // 空闲连接自动关闭
		TLSHandshakeTimeout:   time.Duration(timeOut) * time.Second,
		ResponseHeaderTimeout: time.Duration(timeOut) * time.Second,
		ExpectContinueTimeout: 5 * time.Second,

		// 开启TCP保活，内核自动探测断连僵死连接
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			dialer := &net.Dialer{
				Timeout:   5 * time.Second,
				KeepAlive: 15 * time.Second,
			}
			return dialer.DialContext(ctx, network, addr)
		},
	}
	return transport, timeOut
}

type FileServer struct {
	sessionId string
}

type FileResp struct {
	Code    int    `json:"code"`
	Volume  string `json:"data"`
	Message string `json:"message"`
}

func NewFileServer(sId string) *FileServer {
	fileServer := &FileServer{
		sessionId: sId,
	}
	return fileServer
}

func (f *FileServer) GetHttpClient(fileLen int) *http.Client {
	var httpClient *http.Client
	if fileLen > LONG_FILE {
		httpClient = NewHttpClient(HTTP_LONG)
	} else {
		httpClient = NewHttpClient(HTTP_NORMAL)
	}
	if httpClient == nil {
		httpClient = f.defaultHttp(httpClient)
	}
	return httpClient
}

func (f *FileServer) defaultHttp(httpClient *http.Client) *http.Client {
	timeout := 10
	httpClient = &http.Client{
		Transport: &http.Transport{
			TLSHandshakeTimeout:   time.Duration(timeout) * time.Second,
			ResponseHeaderTimeout: time.Duration(timeout) * time.Second,
		},
		Timeout: time.Duration(timeout) * time.Second,
	}
	return httpClient
}

func (f *FileServer) Upload(urlStr, volume, fileName string, data []byte) (int, *FileResp) {
	if len(data) < 1 {
		log.WarnK(f.sessionId, "doUpload,fileName=%v,dataLen<1", fileName)
	}

	httpClient := f.GetHttpClient(len(data))
	log.InfoK(f.sessionId, "doUpload,urlStr=%v", urlStr)
	req, err := http.NewRequest("POST", urlStr, bytes.NewReader(data))
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,NewRequest, url=%v, err=%v", urlStr, err.Error())
		return CONNECT_ERROR_CODE, nil
	}
	// 设置 Content-Type 为 application/octet-stream
	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set(HEAD_VOLUME, volume)
	req.Header.Set(HEAD_SESSION, f.sessionId)

	log.InfoK(f.sessionId, "doUpload,urlStr=%v,dataSize=%v", urlStr, len(data))

	resp, err := httpClient.Do(req)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,httpRequest,fail,urlStr=%v, err=%v", urlStr, err.Error())
		return CONNECT_ERROR_CODE, nil
	}

	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,read Body,err=%v", err)
	}
	log.InfoK(f.sessionId, "doUpload,response,body=%v,httpCode=%d", string(body), resp.StatusCode)

	fileResp := &FileResp{}
	httpCode := resp.StatusCode
	//log.InfoK(f.sessionId, fmt.Sprintf("%v,doUpload httpCode=%d", fileName, httpCode))
	statusCode := httpCode / 100
	if statusCode == 2 {
		err = json.Unmarshal(body, fileResp)
		if err != nil {
			log.WarnK(f.sessionId, "doUpload,FileResp,err=%v", err)
		}
		return FILE_SUC_CODE, fileResp
	} else if statusCode == 5 {
		// 判断是否 5xx 状态码
		return CONNECT_ERROR_5XX, fileResp
	}
	//返回失败
	return FILE_FAIL_CODE, fileResp
}

func (f *FileServer) UploadFile(urlStr, volume, fileName string, filePath string) (int, *FileResp) {
	var b bytes.Buffer
	w := multipart.NewWriter(&b)
	// Write multipart into buffer
	file, err := os.Open(filePath)
	if err != nil {
		log.WarnK(f.sessionId, "UploadFile,Open=%v,err=%v", filePath, err)
		return FILE_FAIL_CODE, nil
	}

	part, err := w.CreateFormFile("media", fileName)
	if err != nil {
		_ = file.Close()
		log.WarnK(f.sessionId, "UploadFile,part,CreateFormFile,fileName=%v,err=%v", fileName, err)
		return FILE_FAIL_CODE, nil
	}
	_, err = io.Copy(part, file)
	if err != nil {
		_ = file.Close()
		log.WarnK(f.sessionId, "UploadFile,part,copy,fileName=%v,err=%v", fileName, err)
		return FILE_FAIL_CODE, nil
	}
	_ = w.Close()

	fileLen := b.Len()
	log.InfoK(f.sessionId, "doUpload,urlStr=%v", urlStr)
	req, err := http.NewRequest("POST", urlStr, &b)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,NewRequest, url=%v, err=%v", urlStr, err.Error())
		return CONNECT_ERROR_CODE, nil
	}
	req.Header.Set("Content-Type", w.FormDataContentType())
	req.Header.Set(HEAD_VOLUME, volume)
	req.Header.Set(HEAD_SESSION, f.sessionId)

	log.InfoK(f.sessionId, "doUpload,urlStr=%v,dataSize=%v", urlStr, fileLen)

	httpClient := f.GetHttpClient(fileLen)

	resp, err := httpClient.Do(req)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,httpRequest,fail,urlStr=%v, err=%v", urlStr, err.Error())
		return CONNECT_ERROR_CODE, nil
	}

	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,read Body,err=%v", err)
	}

	log.InfoK(f.sessionId, "doUpload,response,body=%v", string(body))

	fileResp := &FileResp{}
	err = json.Unmarshal(body, fileResp)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,FileResp,err=%v", err)
	}

	httpCode := resp.StatusCode
	log.InfoK(f.sessionId, fmt.Sprintf("%v,doUpload httpCode=%d", fileName, httpCode))

	statusCode := httpCode / 100

	if statusCode == 2 {
		return FILE_SUC_CODE, fileResp
	} else if statusCode == 5 {
		// 判断是否 5xx 状态码
		return CONNECT_ERROR_5XX, fileResp
	}
	// 返回失败
	return FILE_FAIL_CODE, fileResp
}

func (f *FileServer) DownFilePost(filePath string, relateName string, downUrl string) (int, []byte) {
	headers := make(map[string]string)
	headers[HEAD_SESSION] = f.sessionId
	paramMap := map[string]interface{}{
		models.FILE_NAME: relateName,
	}
	jsonData, err := json.Marshal(paramMap)
	if err != nil {
		log.WarnK(f.sessionId, "DownFilePost, json marshal error: %v", err)
		return CONNECT_ERROR_CODE, nil
	}

	//正常
	//doDownJsonPost,downUrl=http://10.34.108.18:9090/media/download/Caller_70000000000000288745432538952254_20260422094407.amr,paramsJson={"FileName":"Caller_70000000000000288745432538952254_20260422094407.amr"}
	//失败
	// DownFilePost,downUrl=http://10.34.108.19:9060/media/download,paramsJson={"fileName":"Caller_70000000000000289678211791462423_20260430104702.amr"}

	fullURL := fmt.Sprintf("%s/%s", downUrl, url.QueryEscape(relateName))
	log.InfoK(f.sessionId, "DownFilePost,downUrl=%v", fullURL)
	req, err := http.NewRequest("POST", fullURL, bytes.NewReader(jsonData))
	if err != nil {
		log.WarnK(f.sessionId, "DownFilePost: create request error: %v", err)
		return CONNECT_ERROR_NO_ACCESS, nil
	}

	for k, v := range headers {
		req.Header.Set(k, v)
	}
	req.Header.Set("Content-Type", "application/json")

	httpClient := f.GetHttpClient(1024 * 1024)
	resp, err := httpClient.Do(req)
	if err != nil {
		var netErr net.Error
		if errors.As(err, &netErr) && netErr.Timeout() {
			log.WarnK(f.sessionId, "DownFilePost,url=%v,SocketTimeoutException:%v", fullURL, err)
			return CONNECT_ERROR_READ_TIMEOUT, nil
		}
		log.WarnK(f.sessionId, "DownFilePost,downUrl=%v,http,do,err=%v", fullURL, err)
		return CONNECT_ERROR_NO_ACCESS, nil
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		log.WarnK(f.sessionId, "DownFilePost,HttpClientErrorException,rawCode=%v", resp.StatusCode)
		return CONNECT_ERROR_404, nil
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		log.WarnK(f.sessionId, "DownFilePost,http code error=%v", resp.StatusCode)
		return CONNECT_ERROR_CODE, nil
	}

	err, fileBuf := f.handleResponse(filePath, resp.Body)
	if err != nil {
		log.WarnK(f.sessionId, "DownFilePost,filePath=%v,save file error=%v", filePath, err)
		return CONNECT_ERROR_CODE, nil
	}
	return FILE_SUC_CODE, fileBuf
}

func (f *FileServer) handleResponse(filePath string, body io.Reader) (error, []byte) {
	var buf bytes.Buffer
	var n int64
	tee := io.TeeReader(body, &buf)
	out, err := os.Create(filePath)
	if err != nil {
		return err, nil
	}
	defer out.Close()

	n, err = io.Copy(out, tee)
	if err != nil {
		log.WarnK(f.sessionId, "handleResponse,n=%v, io.Copy err=%v", n, err)
	}

	// copy完成，拿到body完整字节
	bodyBytes := buf.Bytes()
	return err, bodyBytes
}
