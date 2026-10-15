package record

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"time"
)

const (
	PayloadTypeG729 = 18

	// G729SampleSize Frame sample size, 10ms per frame
	G729SampleSize = 80
	// G729MuteFrameSize Mute frame size, two byte
	G729MuteFrameSize = 2
	// G729VoiceFrameSize Voice frame size, 10ms per frame, ten byte
	G729VoiceFrameSize = 10

	// G729MaxMultiFrames 允许的最大补帧帧数
	G729MaxMultiFrames = 25
	// G729MissingFramesLimit 1000s
	G729MissingFramesLimit = 50000
	// G729MissingCompensateThreshold 5s
	G729MissingCompensateThreshold = 5000
	// G729MissingTimeIntervalUpLimit 实际时间间隔上限为600000ms
	G729MissingTimeIntervalUpLimit = 600000
)

var (
	G729MuteFrame    = []byte{0x60, 0x5e, 0x6F, 0x40, 0x00, 0xFA, 0xC1, 0xEF, 0x7A, 0x56}
	G729TwoMuteFrame = bytes.Repeat(G729MuteFrame, 2)
)

type G729 struct {
	BaseInfo
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

	// Internal buffer,
	// used as a temporary buffer during encoding and decoding
	innerBuf []byte
}

func NewG729(bMem, bEarly bool, mimeType, volume, path, name string) (*G729, error) {
	media := &G729{}
	err := media.Init(bMem, bEarly, mimeType, volume, path, name)
	if err != nil {
		return media, err
	}
	media.innerBuf = minCache.Get().([]byte)
	media.timeCreate = time.Now().UnixNano() / int64(time.Millisecond)
	media.timeStack = media.timeCreate
	return media, nil
}

func (f *G729) Write(packet []byte) ([]byte, int, int, error) {
	if !f.Mem() && f.IsDeleted() {
		return nil, 0, 0, nil
	}
	if !f.Mem() && !f.IsWriteable() {
		return nil, 0, 0, errors.New("the file is not writeable")
	}

	if len(packet) < RtpHeaderLength {
		return nil, 0, 0, fmt.Errorf("invalid rtp for g729 written: %v", len(packet))
	}
	// Record and parse
	f.Wrap(packet)
	payloadType := f.PayloadType()
	if payloadType != PayloadTypeG729 {
		return nil, 0, 0, errors.New(fmt.Sprintf("invalid g729 payloadType: %v", payloadType))
	}
	if f.Ssrc() != f.ssrc {
		f.ts = 0
	}
	f.ssrc = f.Ssrc()

	// Write
	frames, nFrames, mFrames, err := f.writeFrames()
	// Flush
	if f.IsEnough(AudioBufThreshold) {
		_, _ = f.Persist()
	}
	return frames, nFrames, mFrames, err
}

func (f *G729) writeFrames() ([]byte, int, int, error) {
	rtpTs := int64(f.Timestamp())
	if f.ts > 0 {
		// Assuming the last RTP timestamp is larger, calculate how many RTP packets are between the two RTP timestamps
		packets := (f.ts-rtpTs)/G729SampleSize - 1
		if packets > 0 && packets < 10000 {
			f.errCounter++
			if f.errCounter < MaxRtpOutOfOrder {
				return nil, 0, 0, fmt.Errorf("the rtp packet arrived too late for g729 written, curTs: %v, rtpTs: %v, length: %v", f.ts, rtpTs, f.Size())
			}
			// Multiple RTP packets are out of order consecutively,
			// simply reset the RTP timestamp
			f.ts = 0
		}
	}
	f.errCounter = 0
	frames := f.Payload()
	if len(frames) < G729MuteFrameSize {
		return nil, 0, 0, fmt.Errorf("invalid rtp for g729 written with empty payload: %v", len(frames))
	}
	// Frame written
	var err error
	mFrames := 0
	nFrames := len(frames) / G729VoiceFrameSize
	timeNowMilli := time.Now().UnixNano() / int64(time.Millisecond)
	if len(frames) == G729MuteFrameSize {
		// Mute frame
		frames = G729MuteFrame
		nFrames = 1
	} else if nFrames*G729VoiceFrameSize != len(frames) {
		// Voice with mute frame
		// Multi frame, when more than 6 frames
		buf := f.innerBuf
		if nFrames >= 6 {
			buf = make([]byte, 0, (nFrames+1)*G729VoiceFrameSize)
		}
		copy(buf, frames[:len(frames)-G729MuteFrameSize])
		copy(buf[len(buf):], G729MuteFrame)
		frames = buf[:(nFrames+1)*G729VoiceFrameSize]
		nFrames++
	}
	// Mute compensation
	_, mFrames, err = f.writeMissingFrames(rtpTs, timeNowMilli, int64(nFrames))
	f.ts = rtpTs
	f.counter += nFrames + mFrames
	f.timeStack = timeNowMilli
	_, err = f.MediaInfo.Write(frames)
	return frames, nFrames, mFrames, err
}

func (f *G729) writeMissingFrames(rtpTs, timeNowMilli int64, nFrames int64) (int, int, error) {
	if f.ts > rtpTs {
		return 0, 0, nil
	}

	// Calculate the number of missing frames
	var nMissingFrames int64
	if f.ts == 0 {
		// Need to remove current RTP
		nMissingFrames = (timeNowMilli-f.timeStack)/AudioFrameMinDuration - nFrames
	} else {
		newTs := rtpTs
		absTimeInterval := timeNowMilli - f.timeStack
		rtpTimeInterval := (rtpTs-f.ts)*AudioFrameMinDuration/G729SampleSize + AudioFrameMinDuration*AmrMaxMultiFrames
		if absTimeInterval > rtpTimeInterval && absTimeInterval < G729MissingTimeIntervalUpLimit {
			newTs = f.ts + G729SampleSize*(absTimeInterval/AudioFrameMinDuration-nFrames)
		}
		nMissingFrames = (newTs-f.ts)/G729SampleSize - nFrames
	}
	// Determine if too many frames are being interpolated based on the actual time interval
	if f.isTooManyFrames(timeNowMilli, nMissingFrames) {
		// Recalculate the number of missing frames based on the actual time interval from the first RTP
		nMissingFrames = (timeNowMilli-f.timeCreate)/AudioFrameMinDuration - int64(f.counter) - nFrames
		if nMissingFrames <= 0 {
			return 0, 0, nil
		}
	}

	var err error
	if nMissingFrames <= 0 {
		return 0, 0, nil
	}
	mFrames, muteFrames := f.generateMissingFrames(nMissingFrames)
	if mFrames > 0 {
		_, err = f.MediaInfo.Write(muteFrames)
		f.ts = rtpTs - nFrames*G729SampleSize
		f.timeStack = timeNowMilli - nFrames*AudioFrameMinDuration
	}
	return len(muteFrames), mFrames, err
}

func (f *G729) generateMissingFrames(nMissingFrames int64) (int, []byte) {
	if nMissingFrames <= 0 || nMissingFrames > G729MissingFramesLimit {
		return 0, nil
	}
	return int(nMissingFrames), bytes.Repeat(G729MuteFrame, int(nMissingFrames))
}

func (f *G729) isTooManyFrames(timeNowMilli, nMissingFrames int64) bool {
	absTimeInterval := timeNowMilli - f.timeCreate
	nWishedFrames := absTimeInterval / AudioFrameMinDuration
	// Allow at most one multi-frame size ahead of time
	return int64(f.counter)+nMissingFrames > nWishedFrames+G729MaxMultiFrames
}

func (f *G729) Delete() error {
	err := f.MediaInfo.Close()
	err = os.Remove(f.FileName())
	if f.innerBuf != nil {
		minCache.Put(f.innerBuf)
	}
	return err
}

func (f *G729) Close() ([]byte, error) {
	if !f.Mem() && !f.IsWriteable() {
		return nil, nil
	}
	if !f.Mem() && f.IsDeleted() {
		return nil, f.Delete()
	}

	// Make up for the remaining mute frames
	if !f.IsEarly() {
		absTimeInterval := time.Now().UnixNano()/int64(time.Millisecond) - f.timeStack
		if absTimeInterval > G729MissingCompensateThreshold {
			nMissingFrames := (absTimeInterval-AudioFrameMinDuration)/AudioFrameMinDuration - 8
			nFrames, muteFrames := f.generateMissingFrames(nMissingFrames)
			if nFrames != 0 {
				_, _ = f.MediaInfo.Write(muteFrames)
			}
		}
	}
	// Flush
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