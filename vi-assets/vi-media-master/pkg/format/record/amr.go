package record

import (
	"errors"
	"fmt"
	"os"
	"sync"
	"time"
)

const (
	// AmrMaxMultiFrames 允许的最大补帧帧数
	AmrMaxMultiFrames = 25
	// AmrMissingFramesLimit 1000s
	AmrMissingFramesLimit = 50000
	// AmrMissingCompensateThreshold 5s
	AmrMissingCompensateThreshold = 5000
	// AmrMissingTimeIntervalUpLimit 实际时间间隔上限为600000ms
	AmrMissingTimeIntervalUpLimit = 600000

	ModeOctetAligned       = 1
	ModeBandwidthEfficient = 2

	AmrNbSampleRate = 8000
	AmrWbSampleRate = 16000
)

var (
	minCache = sync.Pool{New: func() interface{} { return make([]byte, 61) }}

	AmrHeader            = map[string][]byte{MimeTypeAmrNB: {'#', '!', 'A', 'M', 'R', '\n'}, MimeTypeAmrWB: {'#', '!', 'A', 'M', 'R', '-', 'W', 'B', '\n'}}
	AmrSampleRate        = map[string]int{MimeTypeAmrNB: AmrNbSampleRate, MimeTypeAmrWB: AmrWbSampleRate}
	AmrSamplePerFrame    = map[string]int{MimeTypeAmrNB: 160, MimeTypeAmrWB: 320}
	AmrSpeechPayloadSize = map[string]int{MimeTypeAmrNB: 32, MimeTypeAmrWB: 61}

	AmrFrameSize = map[string][]int{MimeTypeAmrNB: {12, 13, 15, 17, 19, 20, 26, 31, 5, 0, 0, 0, 0, 0, 0, 0}, MimeTypeAmrWB: {17, 23, 32, 36, 40, 46, 50, 58, 60, 5, 0, 0, 0, 0, 0, 0}}

	AmrFrameBitSize = map[string][]int{MimeTypeAmrNB: {95, 103, 118, 134, 148, 159, 204, 244, 39, 43, 38, 37, 0, 0, 0, 0}, MimeTypeAmrWB: {132, 177, 253, 285, 371, 365, 397, 461, 477, 40, 0, 0, 0, 0, 0, 0}}

	AmrMuteFrame = map[string][]byte{
		MimeTypeAmrNB: {0x3C, 0x08, 0x55, 0x6D, 0x94, 0x4C, 0x71, 0xA1, 0xA0, 0x81, 0xE7, 0xEA, 0xD2, 0x04, 0x24, 0x44, 0x80, 0x00, 0x0E, 0xCD, 0x82, 0xB8, 0x11, 0x18, 0x00, 0x00, 0x97, 0xC4, 0x79, 0x4E, 0x77, 0x40},
		MimeTypeAmrWB: {68, 17, 6, 48, 51, 190, 206, 179, 160, 211, 0, 0, 235, 80, 135, 180, 255, 214, 66, 64, 24, 8, 26, 229, 2, 42, 150, 145, 41, 72, 73, 203, 82, 34, 137, 6, 120, 192, 40, 0, 177, 24, 139, 147, 36,
			198, 88, 116, 172, 25, 13, 215, 176, 91, 8, 136, 203, 186, 175, 242, 88}}
)

type Amr struct {
	BaseInfo

	// ModeBandwidthEfficient
	// ModeOctetAligned
	mode int
	// amr：8000
	// amr-wb：16000
	sampleRate int

	// The RTP timestamp of the previous packet
	ts int64
	// The number of frames that have been written to the file buffer
	counter int
	// The number of out-of-order frames
	errCounter int
	// The actual timestamp of the previous packet
	timeStack int64
	// The initial timestamp
	timeCreate int64

	// The prev ssrc
	// Used for refreshing ts
	ssrc uint32
	// The prev payloadType
	// Used for refreshing ts
	payloadType uint8
	// Used for frame interpolation when the first frame is sid
	bFirst bool

	// Internal buffer,
	// used as a temporary buffer during encoding and decoding
	innerBuf []byte
}

func NewAmr(bMem, bEarly bool, mode int, mimeType, volume, path, name string) (*Amr, error) {
	media := &Amr{mode: mode}
	err := media.Init(bMem, bEarly, mimeType, volume, path, name)
	if err != nil {
		return media, err
	}
	media.innerBuf = minCache.Get().([]byte)
	media.timeCreate = time.Now().UnixNano() / int64(time.Millisecond)
	media.timeStack = media.timeCreate
	media.sampleRate = AmrSampleRate[mimeType]
	// Write header
	_, _ = media.WriteHeader(AmrHeader[mimeType])
	return media, nil
}

func (f *Amr) Write(packet []byte) ([]byte, int, int, error) {
	if !f.Mem() && f.IsDeleted() {
		return nil, 0, 0, nil
	}
	if !f.Mem() && !f.IsWriteable() {
		return nil, 0, 0, errors.New("the file is not writeable")
	}

	if len(packet) < RtpHeaderLength {
		return nil, 0, 0, fmt.Errorf("invalid rtp for amr written: %v", len(packet))
	}
	// Record and parse
	f.Wrap(packet)
	payloadType := f.PayloadType()
	if payloadType < DynamicPayload {
		return nil, 0, 0, errors.New(fmt.Sprintf("invalid amr payloadType: %v", payloadType))
	}
	// If payloadType or ssrc changes, refresh ts
	f.bFirst = f.payloadType == 0
	if payloadType != f.payloadType || f.Ssrc() != f.ssrc {
		f.ts = 0
	}
	f.payloadType = payloadType
	f.ssrc = f.Ssrc()
	// Write
	frames, nFrames, mFrames, err := f.writeFrames()
	// Flush
	if f.IsEnough(AudioBufThreshold) {
		_, _ = f.Persist()
	}
	return frames, nFrames, mFrames, err
}

func (f *Amr) writeFrames() ([]byte, int, int, error) {
	size := f.Size()
	rtpTs := int64(f.Timestamp())
	if f.ts > 0 {
		// Assuming the last RTP timestamp is larger, calculate how many RTP packets are between the two RTP timestamps
		packets := ((f.ts - rtpTs) / int64(AmrSamplePerFrame[f.MimeType()])) - 1
		if packets > 0 && packets < 10000 {
			f.errCounter++
			if f.errCounter < MaxRtpOutOfOrder {
				return nil, 0, 0, fmt.Errorf("the rtp packet arrived too late for amr written, curTs: %v, rtpTs: %v, length: %v", f.ts, rtpTs, size)
			}
			// Multiple RTP packets are out of order consecutively,
			// simply reset the RTP timestamp
			f.ts = 0
		}
	}
	f.errCounter = 0

	// Mute frame
	if size == 19 {
		_, mFrames, err := f.writeMuteFrames(rtpTs)
		return AmrMuteFrame[f.MimeType()], 1, mFrames, err
	}

	// Voice frame
	var err error
	var speechFrame []byte
	mFrames, nFrames := 0, 0
	timeNowMilli := time.Now().UnixNano() / int64(time.Millisecond)
	if !f.IsEarly() {
		_, mFrames, err = f.writeMissingFrames(true, rtpTs, timeNowMilli, size)
		f.counter += mFrames
	}
	nFrames, speechFrame, err = f.extractSpeechFrame(f.Payload())
	if nFrames == 0 {
		return speechFrame, nFrames, mFrames, err
	}
	f.ts = rtpTs
	f.counter += nFrames
	f.timeStack = timeNowMilli
	_, err = f.MediaInfo.Write(speechFrame)
	return speechFrame, nFrames, mFrames, err
}

func (f *Amr) writeMuteFrames(rtpTs int64) (int, int, error) {
	payload := f.Payload()
	timeNowMilli := time.Now().UnixNano() / int64(time.Millisecond)
	if f.ts == 0 && f.bFirst {
		var frameType byte
		var muteFrameFlag int
		if f.mode == ModeOctetAligned {
			muteFrameFlag = int((payload[6] & 0x02) >> 1)
			frameType = (payload[1] & 0x78) >> 3
		} else {
			muteFrameFlag = int((payload[5] & 0x04) >> 2)
			frameType = (payload[0]&0x07)<<1 | (payload[1]&0x80)>>7
		}
		if muteFrameFlag != 1 {
			return 0, 0, nil
		}
		// 判断是否为非静音错误帧
		if f.isValidMuteFrame(frameType) {
			var muteFrames []byte
			for i := 0; i < 8; i++ {
				muteFrames = append(muteFrames, AmrMuteFrame[f.MimeType()]...)
			}
			f.ts = rtpTs
			f.counter += 8
			f.timeStack = timeNowMilli
			nBytes, err := f.MediaInfo.Write(muteFrames)
			return nBytes, 7, err
		}
		return 0, 0, nil
	}
	if !f.IsEarly() {
		nBytes, nFrames, err := f.writeMissingFrames(false, rtpTs, timeNowMilli, f.Size())
		f.counter += nFrames
		return nBytes, nFrames - 1, err
	}
	return 0, 0, nil
}

func (f *Amr) writeMissingFrames(speechFrame bool, rtpTs int64, timeNowMilli int64, frameSize int) (int, int, error) {
	if f.ts > rtpTs {
		return 0, 0, nil
	}

	// Calculate the number of missing frames
	var nMissingFrames int64
	if f.ts == 0 {
		// -AudioFrameDuration，Remove current RTP
		nMissingFrames = (timeNowMilli - f.timeStack - AudioFrameDuration) / AudioFrameDuration
	} else {
		newTs := rtpTs
		absTimeInterval := timeNowMilli - f.timeStack
		rtpTimeInterval := (rtpTs-f.ts)*AudioFrameDuration/int64(AmrSamplePerFrame[f.MimeType()]) + AudioFrameDuration*AmrMaxMultiFrames
		if absTimeInterval > rtpTimeInterval && absTimeInterval < AmrMissingTimeIntervalUpLimit {
			newTs = f.ts + int64(AmrSamplePerFrame[f.MimeType()])*((absTimeInterval-AudioFrameDuration)/AudioFrameDuration)
		}
		nMissingFrames = (newTs-f.ts)/int64(AmrSamplePerFrame[f.MimeType()]) - 1
	}
	// Determine if too many frames are being interpolated based on the actual time interval
	if f.isTooManyFrames(timeNowMilli, nMissingFrames) {
		// Recalculate the number of missing frames based on the actual time interval from the first RTP
		nMissingFrames = (timeNowMilli-f.timeCreate)/AudioFrameDuration - int64(f.counter) - 1
		if nMissingFrames < 1 {
			return 0, 0, nil
		}
	}

	var err error
	// Voice
	if speechFrame {
		nFrames := frameSize / AmrSpeechPayloadSize[f.MimeType()]
		if nMissingFrames < int64(nFrames) {
			return 0, 0, nil
		}
		nFrames, muteFrames := f.generateMissingFrames(nMissingFrames)
		if nFrames > 0 {
			_, err = f.MediaInfo.Write(muteFrames)
			f.ts = rtpTs - int64(AmrSamplePerFrame[f.MimeType()])
			f.timeStack = timeNowMilli - AudioFrameDuration
		}
		return len(muteFrames), nFrames, err
	}
	// Mute
	nMissingFrames++
	if nMissingFrames < 1 {
		nMissingFrames = 1
	}
	nFrames, muteFrames := f.generateMissingFrames(nMissingFrames)
	_, err = f.MediaInfo.Write(muteFrames)
	f.ts = rtpTs
	f.timeStack = timeNowMilli
	return len(muteFrames), nFrames, err
}

func (f *Amr) generateMissingFrames(nMissingFrames int64) (int, []byte) {
	if nMissingFrames <= 0 || nMissingFrames > AmrMissingFramesLimit {
		return 0, nil
	}
	var muteFrames []byte
	for i := nMissingFrames; i > 0; i-- {
		muteFrames = append(muteFrames, AmrMuteFrame[f.MimeType()]...)
	}
	return int(nMissingFrames), muteFrames
}

func (f *Amr) extractSpeechFrame(payload []byte) (int, []byte, error) {
	if len(payload) < 2 {
		return 0, nil, errors.New(fmt.Sprintf("rtp payload is too short: %v", len(payload)))
	}
	if f.mode == ModeOctetAligned {
		return f.handleOaMode(payload)
	}
	return f.handleBeMode(payload)
}

func (f *Amr) handleBeMode(payload []byte) (int, []byte, error) {
	// packing frame with TOC: frame type and quality bit
	// RTP=[CMR(4bit)[F][FT(4bit)][Q][..speechFrame]] -> storage=[0][FT(4bit)][Q][0][0]
	// cmr := (rtpFrameHeader[0] & 0xF0) >> 4

	rtpFrameHeader := payload[0:]

	isLastFrame := (rtpFrameHeader[0]&0x08)>>3 == 0x00
	frameType := (rtpFrameHeader[0]&0x07)<<1 | (rtpFrameHeader[1]&0x80)>>7
	quality := (rtpFrameHeader[1] & 0x40) == 0x40

	// Multi-Frame
	if !isLastFrame {
		return f.handleMultiFrames(payload)
	}

	if !quality {
		return 0, nil, errors.New("rtp payload contains a bad frame")
	}
	if AmrFrameSize[f.MimeType()][frameType] == 0 {
		return 0, nil, errors.New("amr frameSize is invalid")
	}
	// Single-Frame
	end := 1
	speechFrameHeader := (rtpFrameHeader[0]&0x07)<<4 | (rtpFrameHeader[1]&0x80)>>4
	speechFrameHeader = speechFrameHeader | (rtpFrameHeader[1]&0x40)>>4
	speechFrameSize := AmrFrameSize[f.MimeType()][frameType]
	f.innerBuf[0] = speechFrameHeader
	speechPayload := rtpFrameHeader[1:]
	if len(speechPayload) < speechFrameSize {
		return 0, nil, errors.New(fmt.Sprintf("speechFrame size [%v] does not match frameType [%v], expect: %v", len(speechPayload), frameType, speechFrameSize))
	}
	speechFrame := f.innerBuf[end:]
	for k := 0; k < speechFrameSize; k++ {
		speechFrame[k] = (speechPayload[k] & 0x3F) << 2
		if k+1 < speechFrameSize && len(speechPayload) > k+1 {
			speechFrame[k] = speechFrame[k] | (speechPayload[k+1]&0xC0)>>6
		}
	}
	end += speechFrameSize
	return 1, f.innerBuf[:end], nil
}

func (f *Amr) handleOaMode(payload []byte) (int, []byte, error) {
	// payload header := [CMR(4bit)[R(4bit)][ILL(4bit)(opt)][ILP(4bit)(opt)]，here ignore ILL and interleaving、crc in session
	// TOC := [F][FT(4bit)][Q][P][P]
	// cmr := (rtpFrameHeader[0] & 0xF0) >> 4

	rtpFrameHeader := payload[0:]

	isLastFrame := (rtpFrameHeader[1]&0x80)&0x80 == 0x00
	frameType := (rtpFrameHeader[1] & 0x78) >> 3
	quality := (rtpFrameHeader[1]&0x04)&0x04 == 0x04

	// Multi-Frame
	if !isLastFrame {
		return f.handleMultiFrames(payload)
	}

	if !quality {
		return 0, nil, errors.New("rtp payload contains a bad frame")
	}
	if AmrFrameSize[f.MimeType()][frameType] == 0 {
		return 0, nil, errors.New("amr frameSize is invalid")
	}
	// Single-Frame
	end := 1
	speechFrameHeader := frameType << 3
	speechFrameHeader = speechFrameHeader | (rtpFrameHeader[1] & 0x04)
	speechFrameSize := AmrFrameSize[f.MimeType()][frameType]
	f.innerBuf[0] = speechFrameHeader
	if len(rtpFrameHeader[2:]) < speechFrameSize {
		return 0, nil, errors.New(fmt.Sprintf("speechFrame size [%v] does not match frameType [%v], expect: %v", len(rtpFrameHeader[2:]), frameType, speechFrameSize))
	}
	copy(f.innerBuf[end:], rtpFrameHeader[2:2+speechFrameSize])
	end += speechFrameSize
	return 1, f.innerBuf[:end], nil
}

func (f *Amr) handleMultiFrames(payload []byte) (int, []byte, error) {
	offset := 4
	octetAligned := false
	if f.mode == ModeOctetAligned {
		offset = 8
		octetAligned = true
	}

	// TODO: 多帧情况下内存复用
	var frames []byte
	var tocItems []byte
	for {
		frame, idx := Resolve(payload, offset, Round(6, octetAligned))
		if idx <= 0 {
			break
		}
		if len(frame) == 0 {
			return 0, nil, errors.New("rtp payload contains a bad multiFrame")
		}
		tocItems = append(tocItems, frame[0]&0x7c)
		offset = idx
		if frame[0]&0x80 == 0 {
			break
		}
	}
	nFrames := 0
	for _, toc := range tocItems {
		quality := (toc & 0x04) >> 2
		if quality != 1 {
			break
		}
		frameType := toc >> 3
		frames = append(frames, toc)
		if AmrFrameBitSize[f.MimeType()][frameType] == 0 {
			return 0, nil, errors.New("multi amr frameSize is invalid")
		}
		frame, idx := Resolve(payload, offset, Round(AmrFrameBitSize[f.MimeType()][frameType], octetAligned))
		offset = idx
		frames = append(frames, frame...)
		nFrames++
	}
	return nFrames, frames, nil
}

func (f *Amr) Delete() error {
	err := f.MediaInfo.Close()
	err = os.Remove(f.FileName())
	if f.innerBuf != nil {
		minCache.Put(f.innerBuf)
	}
	return err
}

func (f *Amr) Close() ([]byte, error) {
	if !f.Mem() && !f.IsWriteable() {
		return nil, nil
	}
	if !f.Mem() && f.IsDeleted() {
		return nil, f.Delete()
	}

	// Make up for the remaining mute frames
	if !f.IsEarly() {
		absTimeInterval := time.Now().UnixNano()/int64(time.Millisecond) - f.timeStack
		if absTimeInterval > AmrMissingCompensateThreshold {
			nMissingFrames := (absTimeInterval-AudioFrameDuration)/AudioFrameDuration - 8
			nFrames, muteFrames := f.generateMissingFrames(nMissingFrames)
			if nFrames != 0 {
				_, _ = f.MediaInfo.Write(muteFrames)
			}
		}
	}
	var frames []byte
	if f.Mem() {
		frames, _ = f.Flush()
	}
	_, err := f.Persist()
	_ = f.MediaInfo.Close()
	if f.innerBuf != nil {
		minCache.Put(f.innerBuf)
	}
	return frames, err
}

func (f *Amr) isValidMuteFrame(frameType byte) bool {
	if f.MimeType() == MimeTypeAmrNB {
		return frameType >= 8 && frameType <= 11
	}
	return frameType == 9
}

func (f *Amr) isTooManyFrames(timeNowMilli, nMissingFrames int64) bool {
	absTimeInterval := timeNowMilli - f.timeCreate
	nWishedFrames := absTimeInterval / AudioFrameDuration
	// Allow at most one multi-frame size ahead of time
	return int64(f.counter)+nMissingFrames > nWishedFrames+AmrMaxMultiFrames
}