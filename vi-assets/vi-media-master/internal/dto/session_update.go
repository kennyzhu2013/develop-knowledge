package dto

import (
	"errors"
	"fmt"
	"net"
	"strings"
)

type SessionUpdate struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// RTP audio caller IP
	AudioCallerIp string `json:"audioCallerIp" binding:"required"`
	// RTP audio callee IP
	AudioCalleeIp string `json:"audioCalleeIp" binding:"required"`
	// RTP audio codec,
	// only supported pcma、pcmu、amr、amr-octet、amr-wb、amr-wb-octet、g729
	AudioCode string `json:"audioCode" binding:"required,oneof=pcma pcmu amr amr-octet amr-wb amr-wb-octet g729"`
	// RTP video caller IP
	VideoCallerIp string `json:"videoCallerIp"`
	// RTP video callee IP
	VideoCalleeIp string `json:"videoCalleeIp"`
	// RTP video codec with framerate,
	// only supported h264-25、h264-30、h264-50、h264-60
	VideoCode string `json:"videoCode"`
	// RTP audio caller port
	AudioCallerPort int `json:"audioCallerPort" binding:"required,gt=0"`
	// RTP audio callee port
	AudioCalleePort int `json:"audioCalleePort" binding:"required,gt=0"`
	// RTP audio payloadType
	AudioPayload uint8 `json:"audioPayload"`
	// RTP audio telephone-event payloadType, refresh only when DTMF is already enabled
	AudioDtmfPayload uint8 `json:"audioDtmfPayload"`
	// RTP video caller port
	VideoCallerPort int `json:"videoCallerPort"`
	// RTP video callee port
	VideoCalleePort int `json:"videoCalleePort"`
	// RTP video payloadType
	VideoPayload uint8 `json:"videoPayload"`
	// RTP video observe, when the camera is turned on
	VideoObserve int `json:"videoObserve"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`

	AudioCallerAddr *net.UDPAddr
	AudioCalleeAddr *net.UDPAddr
	VideoCallerAddr *net.UDPAddr
	VideoCalleeAddr *net.UDPAddr
}

func (m *SessionUpdate) Validate() error {
	audioCallerAddr, err := net.ResolveUDPAddr("udp", fmt.Sprintf("%s:%d", m.AudioCallerIp, m.AudioCallerPort))
	if err != nil {
		return fmt.Errorf("resolve audio caller addr failed: %v", err)
	}
	audioCalleeAddr, err := net.ResolveUDPAddr("udp", fmt.Sprintf("%s:%d", m.AudioCalleeIp, m.AudioCalleePort))
	if err != nil {
		return fmt.Errorf("resolve audio callee addr failed: %v", err)
	}
	var videoCallerAddr *net.UDPAddr
	var videoCalleeAddr *net.UDPAddr
	if len(m.VideoCallerIp) != 0 && m.VideoCallerPort > 0 && m.VideoPayload > 0 {
		videoCallerAddr, err = net.ResolveUDPAddr("udp", fmt.Sprintf("%s:%d", m.VideoCallerIp, m.VideoCallerPort))
		if err != nil {
			return fmt.Errorf("resolve video caller addr failed: %v", err)
		}
	}
	if len(m.VideoCalleeIp) != 0 && m.VideoCalleePort > 0 && m.VideoPayload > 0 {
		videoCalleeAddr, err = net.ResolveUDPAddr("udp", fmt.Sprintf("%s:%d", m.VideoCalleeIp, m.VideoCalleePort))
		if err != nil {
			return fmt.Errorf("resolve video callee addr failed: %v", err)
		}
	}
	if videoCallerAddr != nil || videoCalleeAddr != nil {
		m.VideoCode = strings.ToLower(m.VideoCode)
		if m.VideoCode != "h264-25" && m.VideoCode != "h264-30" && m.VideoCode != "h264-50" && m.VideoCode != "h264-60" {
			return errors.New("invalid videoCode")
		}
		m.VideoCode = "video/" + m.VideoCode
	}
	if m.VideoObserve == 1 && videoCallerAddr == nil && videoCalleeAddr == nil {
		return errors.New("invalid videoObserve")
	}
	m.AudioCode = "audio/" + m.AudioCode
	m.AudioCallerAddr = audioCallerAddr
	m.AudioCalleeAddr = audioCalleeAddr
	m.VideoCallerAddr = videoCallerAddr
	m.VideoCalleeAddr = videoCalleeAddr
	return nil
}