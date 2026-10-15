package httpinterface

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net"
	"net/http"
	"net/url"
	"sync"
	"time"
	"vi-merge/conf"
	"vi-merge/internal/metrics"
	"vi-merge/internal/models"
	"vi-merge/internal/pkg/log"
)

const (
	FILE_NAME          = "fileName"
	FILE_SUC_CODE      = 0  // 上传成功码
	CONNECT_ERROR_CODE = -1 // 连接错误码
	CONNECT_ERROR_5XX  = -2 // 5xx错误，服务问题
	FILE_FAIL_CODE     = -3 // 上传失败码

	HTTP_NORMAL = "httpNormal"
	HTTP_LONG   = "httpLong"
	LONG_FILE   = 6 * 1024 * 1024
)

var (
	clientMap sync.Map
)

func HttpClient(name string) *http.Client {
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

type FileServer struct {
	sessionId string
}

/*
*
{"code":200,"data":"/nfsdisk/store","message":"success"}
*/
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

// 定时清理任务，后台运行兜底清理僵死连接.TODO：暂时不用
func StartCleanTicker(interval time.Duration) {
	ticker := time.NewTicker(interval)
	go func() {
		for range ticker.C {
			CleanIdleConn()
		}
	}()
}

// CleanIdleConn 主动清理所有空闲连接，释放滞留readLoop协程
func CleanIdleConn() {
	client := HttpClient(HTTP_NORMAL)
	if tr, ok := client.Transport.(*http.Transport); ok {
		tr.CloseIdleConnections()
	}
}

func (f *FileServer) UploadTest(urlStr, path, volume, fileName string, data []byte) (int, *FileResp) {
	fileResp := &FileResp{
		Code:    FILE_SUC_CODE,
		Volume:  "/nfsdisk/store",
		Message: "success",
	}

	time.Sleep(20 * time.Millisecond)
	urlTail := fmt.Sprintf("path=%v&volume=%v", url.QueryEscape(path), url.QueryEscape(volume))
	fullURL := fmt.Sprintf("%v/%v?%v", urlStr, fileName, urlTail)
	log.InfoK(f.sessionId, "doUpload,path=%v,uploadUrl=%v,dataSize=%v", path, fullURL, len(data))
	return FILE_SUC_CODE, fileResp
}

func (f *FileServer) Upload(urlStr, path, volume, fileName string, data []byte) (int, *FileResp) {
	if len(data) < 1 {
		log.WarnK(f.sessionId, "doUpload,fileName=%v,dataLen<1", fileName)
	}

	httpClient := HttpClient(HTTP_NORMAL)
	startTime := time.Now()
	//params := url.Values{}
	//params.Add(FILE_NAME, fileName)

	urlTail := fmt.Sprintf("path=%v&volume=%v", url.QueryEscape(path), url.QueryEscape(volume))
	//urlTail := fmt.Sprintf("path=%v&volume=%v", path, volume)
	//fullURL := fmt.Sprintf("%v/%v?path=%v&volume=%v", urlStr, fileName, path, volume)

	fullURL := fmt.Sprintf("%v/%v?%v", urlStr, fileName, urlTail)

	//log.InfoK(f.sessionId, "doUpload,fullURL=%v", fullURL)
	req, err := http.NewRequest("POST", fullURL, bytes.NewReader(data))
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,NewRequest,url=%v, err=%v", fullURL, err.Error())
		return CONNECT_ERROR_CODE, nil
	}
	// 设置 Content-Type 为 application/octet-stream
	req.Header.Set("Content-Type", "application/octet-stream")

	//client := &http.Client{}
	log.InfoK(f.sessionId, "doUpload,path=%v,uploadUrl=%v,dataSize=%v", path, fullURL, len(data))

	resp, err := httpClient.Do(req)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,httpRequest,fail, url=%v, err=%v", fullURL, err.Error())
		return CONNECT_ERROR_CODE, nil
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,read Body,err=%v", err)
	}

	spTime := time.Since(startTime).Milliseconds()
	log.InfoK(f.sessionId, "doUpload,response,body=%v,sp=%v", string(body), spTime)
	fileResp := &FileResp{}
	err = json.Unmarshal(body, fileResp)
	if err != nil {
		log.WarnK(f.sessionId, "doUpload,FileResp,err=%v", err)
	}

	httpCode := resp.StatusCode
	//log.InfoK(f.sessionId, fmt.Sprintf("%s,doUpload httpCode=%d", fileName, httpCode))
	metrics.StreamMergeUploadTimeObserve(models.MergeWav, float64(spTime))

	statusCode := httpCode / 100

	if statusCode == 2 {
		return FILE_SUC_CODE, fileResp
	} else if statusCode == 5 {
		// 判断是否 5xx 状态码
		return CONNECT_ERROR_5XX, fileResp
	}

	//返回失败
	return FILE_FAIL_CODE, fileResp
}

func (f *FileServer) UploadMultipartFile(urlStr, path, zone, volume, fileName string, data []byte) (int, *FileResp) {
	if len(fileName) < 1 {
		log.WarnK(f.sessionId, "doUpload,fileName=%v,dataLen<1", fileName)
	}

	startTime := time.Now()
	bodyBuf := &bytes.Buffer{}
	bodyWriter := multipart.NewWriter(bodyBuf)
	fileWriter, err := bodyWriter.CreateFormFile("media", fileName)
	if err != nil {
		log.WarnK(f.sessionId, "Upload,bodyWriter,CreateFormFile,fileName=%v,err=%v", fileName, err)
		return FILE_FAIL_CODE, nil
	}

	defer func() {
		bodyBuf.Reset()
		bodyBuf = nil
	}()

	if _, err = io.Copy(fileWriter, bytes.NewReader(data)); err != nil {
		log.WarnK(f.sessionId, "Upload,fileWriter,copy,fileName=%v,err=%v", fileName, err)
		return FILE_FAIL_CODE, nil
	}

	// 必须Close multipart.Writer，补齐末尾boundary分隔符，否则服务端解析失败
	if err = bodyWriter.Close(); err != nil {
		log.WarnK(f.sessionId, "Upload,bodyWriter,Close,fileName=%v,err=%v", fileName, err)
		return FILE_FAIL_CODE, nil
	}

	// ?zone=ZoneM 合音；?zone=ZoneC 分片
	fullURL := fmt.Sprintf("%v/%v?path=%v&zone=%v&volume=%v", urlStr, fileName, path, zone, volume)
	req, err := http.NewRequest("POST", fullURL, bodyBuf)
	if err != nil {
		log.WarnK(f.sessionId, "Upload,NewRequest,url=%v, err=%v", fullURL, err.Error())
		return CONNECT_ERROR_CODE, nil
	}

	//req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("Content-Type", bodyWriter.FormDataContentType())

	//client := &http.Client{}
	log.InfoK(f.sessionId, "Upload,path=%v,uploadUrl=%v", path, fullURL)

	var httpClient *http.Client
	if len(data) > LONG_FILE {
		httpClient = HttpClient(HTTP_LONG)
	} else {
		httpClient = HttpClient(HTTP_NORMAL)
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		log.WarnK(f.sessionId, "Upload,httpRequest,fail, url=%v, err=%v", fullURL, err.Error())
		return CONNECT_ERROR_CODE, nil
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.WarnK(f.sessionId, "Upload,read Body,err=%v", err)
	}

	spTime := time.Since(startTime).Milliseconds()
	log.InfoK(f.sessionId, "Upload,response,body=%v,sp=%v", string(body), spTime)
	fileResp := &FileResp{}
	err = json.Unmarshal(body, fileResp)
	if err != nil {
		log.WarnK(f.sessionId, "Upload,FileResp,err=%v", err)
	}

	httpCode := resp.StatusCode
	//log.InfoK(f.sessionId, fmt.Sprintf("%s,doUpload httpCode=%d", fileName, httpCode))

	statusCode := httpCode / 100

	if statusCode == 2 {
		return FILE_SUC_CODE, fileResp
	} else if statusCode == 5 {
		// 判断是否 5xx 状态码
		return CONNECT_ERROR_5XX, fileResp
	}

	metrics.StreamMergeUploadTimeObserve(models.MergeWav, float64(spTime))

	//返回失败
	return FILE_FAIL_CODE, fileResp
}
