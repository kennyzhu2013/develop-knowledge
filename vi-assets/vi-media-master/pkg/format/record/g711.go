package record

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"time"
)

const (
	PayloadTypePCMU = 0
	PayloadTypePCMA = 8

	// G711MaxMultiFrames 允许的最大补帧帧数
	G711MaxMultiFrames = 25
	// G711MissingFramesLimit 1000s
	G711MissingFramesLimit = 50000
	// G711MissingCompensateThreshold 5s
	G711MissingCompensateThreshold = 5000
	// G711MissingTimeIntervalUpLimit 实际时间间隔上限为600000ms
	G711MissingTimeIntervalUpLimit = 600000
)

var G711MuteFrame = map[string][]byte{MimeTypePCMA: bytes.Repeat([]byte{0xD5}, 160), MimeTypePCMU: bytes.Repeat([]byte{0xFF}, 160)}

type G711 struct {
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
	// The content size that has been persisted
	fileLength int
	// The prev ssrc
	// Used for refreshing ts
	ssrc uint32
}

func NewG711(bMem, bEarly bool, mimeType, volume, path, name string) (*G711, error) {
	media := &G711{}
	err := media.Init(bMem, bEarly, mimeType, volume, path, name)
	if err != nil {
		return media, err
	}
	media.timeCreate = time.Now().UnixNano() / int64(time.Millisecond)
	media.timeStack = media.timeCreate
	// Write 44byte header
	dataLen := Int2Hex(int32(0))
	riffLen := Int2Hex(int32(0 + 50))
	header := append([]byte{'R', 'I', 'F', 'F'}, riffLen...)
	if mimeType == MimeTypePCMA {
		header = append(header, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ', 18, 0, 0, 0, 6, 0, 1, 0, 64, 31, 0, 0, 64, 31, 0, 0, 1, 0, 8, 0, 0, 0, 'f', 'a', 'c', 't', 4, 0, 0, 0)
	} else if mimeType == MimeTypePCMU {
		header = append(header, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ', 18, 0, 0, 0, 7, 0, 1, 0, 64, 31, 0, 0, 64, 31, 0, 0, 1, 0, 8, 0, 0, 0, 'f', 'a', 'c', 't', 4, 0, 0, 0)
	}
	header = append(header, dataLen...)
	header = append(header, 'd', 'a', 't', 'a')
	header = append(header, dataLen...)
	_, _ = media.WriteHeader(header)
	return media, nil
}

func (f *G711) Write(packet []byte) ([]byte, int, int, error) {
	if !f.Mem() && f.IsDeleted() {
		return nil, 0, 0, nil
	}
	if !f.Mem() && !f.IsWriteable() {
		return nil, 0, 0, errors.New("the file is not writeable")
	}

	if len(packet) < RtpHeaderLength {
		return nil, 0, 0, fmt.Errorf("invalid rtp for g711 written: %v", len(packet))
	}
	// Record and parse
	f.Wrap(packet)
	payloadType := f.PayloadType()
	if payloadType != PayloadTypePCMA && payloadType != PayloadTypePCMU {
		return nil, 0, 0, errors.New(fmt.Sprintf("invalid g711 payloadType: %v", payloadType))
	}
	if f.Ssrc() != f.ssrc {
		f.ts = 0
	}
	f.ssrc = f.Ssrc()

	// Write
	frames, nFrames, mFrames, err := f.writeFrames()
	// Flush
	if f.IsEnough(AudioBufThreshold) {
		writeLen, _ := f.Persist()
		f.fileLength += writeLen
	}
	return frames, nFrames, mFrames, err
}

func (f *G711) writeFrames() ([]byte, int, int, error) {
	size := f.Size()
	rtpTs := int64(f.Timestamp())
	if f.ts > 0 {
		// Assuming the last RTP timestamp is larger, calculate how many RTP packets are between the two RTP timestamps
		packets := (f.ts-rtpTs)/160 - 1
		if packets > 0 && packets < 10000 {
			f.errCounter++
			if f.errCounter < MaxRtpOutOfOrder {
				return nil, 0, 0, fmt.Errorf("the rtp packet arrived too late for g711 written, curTs: %v, rtpTs: %v, length: %v", f.ts, rtpTs, size)
			}
			// Multiple RTP packets are out of order consecutively,
			// simply reset the RTP timestamp
			f.ts = 0
		}
	}
	f.errCounter = 0
	// Voice frame
	var err error
	mFrames := 0
	timeNowMilli := time.Now().UnixNano() / int64(time.Millisecond)
	if !f.IsEarly() {
		_, mFrames, err = f.writeMissingFrames(rtpTs, timeNowMilli)
		f.counter += mFrames
	}
	f.ts = rtpTs
	f.counter += 1
	f.timeStack = timeNowMilli
	frames := f.Payload()
	_, err = f.MediaInfo.Write(frames)
	return frames, len(frames) / 160, mFrames, err
}

func (f *G711) writeMissingFrames(rtpTs int64, timeNowMilli int64) (int, int, error) {
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
		rtpTimeInterval := (rtpTs-f.ts)*AudioFrameDuration/160 + AudioFrameDuration*G711MaxMultiFrames
		if absTimeInterval > rtpTimeInterval && absTimeInterval < G711MissingTimeIntervalUpLimit {
			newTs = f.ts + 160*((absTimeInterval-AudioFrameDuration)/AudioFrameDuration)
		}
		nMissingFrames = (newTs-f.ts)/160 - 1
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
	nFrames := f.Size() / 160
	if nMissingFrames < int64(nFrames) {
		return 0, 0, nil
	}
	nFrames, muteFrames := f.generateMissingFrames(nMissingFrames)
	if nFrames > 0 {
		_, err = f.MediaInfo.Write(muteFrames)
		f.ts = rtpTs - 160
		f.timeStack = timeNowMilli - AudioFrameDuration
	}
	return len(muteFrames), nFrames, err
}

func (f *G711) generateMissingFrames(nMissingFrames int64) (int, []byte) {
	if nMissingFrames <= 0 || nMissingFrames > G711MissingFramesLimit {
		return 0, nil
	}
	var muteFrames []byte
	for i := nMissingFrames; i > 0; i-- {
		muteFrames = append(muteFrames, G711MuteFrame[f.MimeType()]...)
	}
	return int(nMissingFrames), muteFrames
}

func (f *G711) isTooManyFrames(timeNowMilli, nMissingFrames int64) bool {
	absTimeInterval := timeNowMilli - f.timeCreate
	nWishedFrames := absTimeInterval / AudioFrameDuration
	// Allow at most one multi-frame size ahead of time
	return int64(f.counter)+nMissingFrames > nWishedFrames+G711MaxMultiFrames
}

func (f *G711) Delete() error {
	err := f.MediaInfo.Close()
	err = os.Remove(f.FileName())
	return err
}

func (f *G711) Close() ([]byte, error) {
	if !f.Mem() && !f.IsWriteable() {
		return nil, nil
	}
	if !f.Mem() && f.IsDeleted() {
		return nil, f.Delete()
	}

	// Make up for the remaining mute frames
	if !f.IsEarly() {
		absTimeInterval := time.Now().UnixNano()/int64(time.Millisecond) - f.timeStack
		if absTimeInterval > G711MissingCompensateThreshold {
			nMissingFrames := (absTimeInterval-AudioFrameDuration)/AudioFrameDuration - 8
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
	writeLen, err := f.Persist()
	f.fileLength += writeLen
	// Reset header
	dataLen := Int2Hex(int32(f.fileLength))
	riffLen := Int2Hex(int32(f.fileLength + 50))
	header := append([]byte{'R', 'I', 'F', 'F'}, riffLen...)
	if f.MimeType() == MimeTypePCMA {
		header = append(header, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ', 18, 0, 0, 0, 6, 0, 1, 0, 64, 31, 0, 0, 64, 31, 0, 0, 1, 0, 8, 0, 0, 0, 'f', 'a', 'c', 't', 4, 0, 0, 0)
	} else if f.MimeType() == MimeTypePCMU {
		header = append(header, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ', 18, 0, 0, 0, 7, 0, 1, 0, 64, 31, 0, 0, 64, 31, 0, 0, 1, 0, 8, 0, 0, 0, 'f', 'a', 'c', 't', 4, 0, 0, 0)
	}
	header = append(header, dataLen...)
	header = append(header, 'd', 'a', 't', 'a')
	header = append(header, dataLen...)
	_, _ = f.SetHeader(header)
	_ = f.MediaInfo.Close()
	return frames, err
}