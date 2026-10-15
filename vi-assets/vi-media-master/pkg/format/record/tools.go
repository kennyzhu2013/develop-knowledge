package record

import (
	"bytes"
	"encoding/binary"
	"sync"
)

const (
	SuffixAmr  = ".amr"
	SuffixWav  = ".wav"
	SuffixG729 = ".g729"
	SuffixH264 = ".h264"
)

const (
	MimeTypeAmrNB      = "audio/amr"
	MimeTypeAmrNBOctet = "audio/amr-octet"
	MimeTypeAmrWB      = "audio/amr-wb"
	MimeTypeAmrWBOctet = "audio/amr-wb-octet"
	MimeTypePCMA       = "audio/pcma"
	MimeTypePCMU       = "audio/pcmu"
	MimeTypeG729       = "audio/g729"
	MimeTypeLowH264    = "video/h264-25"
	MimeTypeMidH264    = "video/h264-30"
	MimeTypeHigH264    = "video/h264-50"
	MimeTypeExtH264    = "video/h264-60"
)

const (
	// MaxRtpOutOfOrder 最大RTP乱序个数
	MaxRtpOutOfOrder = 5

	// AudioFrameDuration 音频帧时长20ms
	AudioFrameDuration = 20
	// AudioFrameMinDuration The min duration for per audio frame, 10ms
	AudioFrameMinDuration = 10

	// AudioBufMinSize 音频文件缓冲区初始化大小
	AudioBufMinSize = 488000
	// AudioBufThreshold 音频文件缓冲区刷出阈值
	AudioBufThreshold = 97600
	// VideoBufMinSize 视频文件缓冲区初始化大小
	VideoBufMinSize = 300 * 1500
	// VideoBufThreshold 视频文件缓冲区刷出阈值
	VideoBufThreshold = VideoBufMinSize - 1500
)

var writeCache = sync.Pool{New: func() interface{} { return bytes.NewBuffer(make([]byte, 0, AudioBufMinSize)) }}

// GetTimestamp 获取RTP的时间戳
func GetTimestamp(time []byte) int64 {
	return int64(time[0])*256*256*256 + int64(time[1])*256*256 + int64(time[2])*256 + int64(time[3])
}

// Int2Hex int转byte数组，再高低位反转，用于wav头部的riff长度，data长度和fact采样数
func Int2Hex(num int32) []byte {
	var buffer bytes.Buffer
	_ = binary.Write(&buffer, binary.BigEndian, num)
	res := buffer.Bytes()
	res[0], res[1], res[2], res[3] = res[3], res[2], res[1], res[0]
	return res
}

func Resolve(data []byte, offset, n int) ([]byte, int) {
	res := make([]byte, 0)
	if n < 0 || n+offset > 8*len(data) {
		return res, offset
	}
	if n == 0 {
		return res, offset
	}
	i := offset >> 3
	sh := uint(offset % 8)
	x := data[i]
	for j := 0; j < (n+7)>>3; j++ {
		value := (x << sh) & 0xFF
		i++
		if i < len(data) {
			x = data[i]
			value |= x >> (8 - sh)
		}
		res = append(res, value)
	}
	offset += n
	nb := uint(8 - n%8)
	if nb < 8 {
		mask := 0x100 - (1 << nb)
		res[len(res)-1] = byte(int(res[len(res)-1]) & mask)
	}
	return res, offset
}

func Round(n int, mode bool) int {
	if mode {
		n = (n + 7) >> 3 << 3
	}
	return n
}

func Mute(mimeType string) []byte {
	if mimeType == MimeTypeG729 {
		return G729TwoMuteFrame
	}
	if mimeType == MimeTypePCMA || mimeType == MimeTypePCMU {
		return G711MuteFrame[mimeType]
	}
	if mimeType == MimeTypeAmrNB || mimeType == MimeTypeAmrWB {
		return AmrMuteFrame[mimeType]
	}
	return nil
}

func FrameSize(mimeType string) int {
	if mimeType == MimeTypeG729 {
		return 20
	}
	if mimeType == MimeTypePCMA || mimeType == MimeTypePCMU {
		return 160
	}
	if mimeType == MimeTypeAmrNB || mimeType == MimeTypeAmrWB {
		return AmrSpeechPayloadSize[mimeType]
	}
	return 0
}

func FrameNumber(mimeType string, dataLen int) int {
	if mimeType == MimeTypePCMA || mimeType == MimeTypePCMU {
		return dataLen / 160
	}
	if mimeType == MimeTypeAmrNB || mimeType == MimeTypeAmrWB {
		return dataLen / AmrSpeechPayloadSize[mimeType]
	}
	return dataLen / 20
}

func Frames(mimeType string, frames int) int {
	if mimeType == MimeTypeG729 {
		return frames >> 1
	}
	return frames
}