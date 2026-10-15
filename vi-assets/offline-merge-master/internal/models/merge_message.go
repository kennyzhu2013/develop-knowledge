package models

import (
	"encoding/json"
	"fmt"
	"strings"
)

type MediaMerge struct {
	// Session unique ID
	SessionID string `json:"sessionId"`

	// Caller num
	CallerNum string `json:"callerNum"`

	// Callee num
	CalleeNum string `json:"calleeNum"`

	// 企业ID
	UserID string `json:"userId"`

	MsID string `json:"msId"`

	// Media application address
	Address string `json:"address"`

	// Media application version
	Version string `json:"version"`

	// Storage volume
	Volume string `json:"volume"`

	// Relative storage sub path
	Path string `json:"path"`

	// A list of caller audio filenames, separated by commas
	CallerAudioFile string `json:"callerAudioFile"`

	// A list of callee audio filenames, separated by commas
	CalleeAudioFile string `json:"calleeAudioFile"`

	// A list of caller video filenames, separated by commas
	CallerVideoFile string `json:"callerVideoFile"`

	// A list of callee video filenames, separated by commas
	CalleeVideoFile string `json:"calleeVideoFile"`

	// A list of early audio filenames, separated by commas
	EarlyAudioFile string `json:"earlyAudioFile"`

	//合音参数
	Options string `json:"options"`

	// Whether to downgrade
	Fallback bool `json:"fallback"`

	// Start time, the time when the initial signaling is received
	StartTime int64 `json:"startTime"`

	// The total audio rtp sending of the platform or the side, include ringing
	CallerAudioRtpCount       int `json:"callerAudioRtpCount"`
	CalleeAudioRtpCount       int `json:"calleeAudioRtpCount"`
	CallerAudioInCallRtpCount int `json:"callerAudioInCallRtpCount"`
	CalleeAudioInCallRtpCount int `json:"calleeAudioInCallRtpCount"`
	CallerAudioRecordCount    int `json:"callerAudioRecordCount"`
	CalleeAudioRecordCount    int `json:"calleeAudioRecordCount"`

	// Video 相关计数
	CallerVideoRtpCount    int `json:"callerVideoRtpCount"`
	CalleeVideoRtpCount    int `json:"calleeVideoRtpCount"`
	CallerVideoRecordCount int `json:"callerVideoRecordCount"`
	CalleeVideoRecordCount int `json:"calleeVideoRecordCount"`

	PcmFormat string `json:"pcmFormat"`
	PcmSample int    `json:"pcmSample"`

	//20260509-chenqing
	UserConfig string `json:"config"`
	Config     Config `json:"-"`
}

type Config struct {
	//质检专用：1:left，2：right，3-双声道
	Source   int    `json:"source"`
	UserDate string `json:"userData"`
}

func (m *MediaMerge) GetPcmAttr() PcmAttr {
	format := "s16le"
	sample := 8000
	if len(m.PcmFormat) > 0 {
		format = m.PcmFormat
	}

	if m.PcmSample > 0 {
		sample = m.PcmSample
	}
	return PcmAttr{
		Ac:     1,
		Sample: sample,
		Format: format,
	}
}

func (m *MediaMerge) ToString() string {
	aStr := fmt.Sprintf(
		"MediaRecordInfo{"+
			"sessionId='%v', "+
			"callerNum='%v', "+
			"calleeNum='%v', "+
			"userId='%v', "+
			"msId='%v', "+
			"address='%v', "+
			"version='%v', "+
			"volume='%v', "+
			"path='%v', "+
			"callerAudioFile='%v', "+
			"calleeAudioFile='%v', "+
			"earlyAudioFile='%v', "+
			"options='%v', "+
			"startTime=%d, "+
			"callerAudioRtpCount=%d, "+
			"calleeAudioRtpCount=%d, "+
			"callerAudioInCallRtpCount=%d, "+
			"calleeAudioInCallRtpCount=%d, "+
			"callerAudioRecordCount=%d, "+
			"calleeAudioRecordCount=%d, "+
			"callerVideoRecordCount=%d, "+
			"calleeVideoRecordCount=%d, "+
			"pcmFormat='%v', "+
			"pcmSample=%d"+
			"}",
		m.SessionID,
		m.CallerNum,
		m.CalleeNum,
		m.UserID,
		m.MsID,
		m.Address,
		m.Version,
		m.Volume,
		m.Path,
		m.CallerAudioFile,
		m.CalleeAudioFile,
		m.EarlyAudioFile,
		m.Options,
		m.StartTime,
		m.CallerAudioRtpCount,
		m.CalleeAudioRtpCount,
		m.CallerAudioInCallRtpCount,
		m.CalleeAudioInCallRtpCount,
		m.CallerAudioRecordCount,
		m.CalleeAudioRecordCount,
		m.CallerVideoRecordCount,
		m.CalleeVideoRecordCount,
		m.PcmFormat,
		m.PcmSample,
	)

	bStr := ""
	if len(m.CallerVideoFile) > 0 {
		bStr = fmt.Sprintf(
			"callerVideoFile='%v', "+
				"calleeVideoFile='%v', "+
				"callerVideoRtpCount=%d, "+
				"calleeVideoRtpCount=%d, ",
			m.CallerVideoFile,
			m.CalleeVideoFile,
			m.CallerVideoRtpCount,
			m.CalleeVideoRtpCount,
		)
	}

	return aStr + bStr
}

func (m *MediaMerge) GetMediaUrl() string {
	if len(m.Address) > 0 {
		// TODO:bug fixed
		if strings.HasPrefix(m.Address, "http") {
			return m.Address
		} else {
			return "http://" + m.Address
		}
	}
	return ""

}

type MergeOptions struct {
	// 配置参数--合成格式
	RecordSuffix string `json:"recordSuffix"`

	// 配置参数--左右腿需要生成指定格式的文件
	SeparateLeg bool `json:"separateLeg"`

	// 配置参数--声道“主左被右”，true=主右被左
	ReverseLeg bool `json:"reverseLeg"`

	// 配置参数--是否分开早期媒体
	SeparateEarly bool `json:"separateEarly"`

	// 配置参数--是否录音
	IsRecord bool `json:"isRecord"`

	// 绑定ID
	BindId string `json:"bindId"`

	////声道数量
	//Channel int `json:"channel"`

	//业务模型，这里判断是是DCC业务,还是质检VI业务。dccMerge/voiceInspection
	Mode string `json:"mode"`

	// 20260813 增加，用于透传给话单
	UserData json.RawMessage `json:"userData"`

	UserDataText string `json:"-"`
}
