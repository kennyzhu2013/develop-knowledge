package customer

import (
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"time"
	"vi-merge/internal/pkg/log"
)

type QaParams struct {
	//可能是url、可能是file
	Uri string `json:"uri,omitempty"`

	//会话ID
	SessionId string `json:"session_id,omitempty"`

	//分片ID
	SegmentId uint32

	//企业ID
	CompanyId string

	//分片时长
	Duration int
}

type SoundQualityInspection struct {
	sessionId  string
	httpClient *http.Client
	//qaParams   QaParams
}

func NewSoundQualityInspection(sId string) *SoundQualityInspection {
	soundQualityInspection := &SoundQualityInspection{
		sessionId: sId,
	}
	return soundQualityInspection
}

func (s *SoundQualityInspection) Init(timeOut int) {
	s.httpClient = &http.Client{
		Transport: &http.Transport{
			TLSHandshakeTimeout:   time.Duration(timeOut) * time.Second,
			ResponseHeaderTimeout: time.Duration(timeOut) * time.Second,
		},
		Timeout: time.Duration(timeOut) * time.Second,
	}

}

func (s *SoundQualityInspection) HttpRequest(qaUrl string, qaParams QaParams) (string, error) {
	reqParams, err := json.Marshal(qaParams)
	if err != nil {
		log.WarnK(s.sessionId, "SoundQualityInspection,HttpRequest,Marshal,err=%v", err)
		return "", err
	}

	req, err := http.NewRequest("POST", qaUrl, strings.NewReader(string(reqParams)))
	if err != nil {
		log.WarnK(s.sessionId, "SoundQualityInspection,HttpRequest,NewRequest,err=%v", err)
		return "", err
	}

	//鉴权头部，TODO:增加
	hm := make(map[string]string, 0)
	for k, v := range hm {
		req.Header.Add(k, v)
	}

	req.Header.Set("Content-Type", "application/json")
	resp, err := s.httpClient.Do(req)
	if err != nil {
		log.WarnK(s.sessionId, "SoundQualityInspection,HttpRequest,httpClient.Do,err=%v", err)
		return "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.WarnK(s.sessionId, "SoundQualityInspection,HttpRequest,ReadAll,Body,err=%v", err)
		return "", err
	}

	log.InfoK(s.sessionId, "SoundQualityInspection,HttpRequest,ReadAll,Body,StatusCode=%v, body=%v",
		resp.StatusCode, string(body))

	return "", nil
}
