package record

import (
	"encoding/binary"
	"errors"
	"fmt"
	"os"
)

const (
	NALUTypeFua   = 28
	NALUTypeStapA = 24

	stapAHeaderSize     = 1
	stapANALULengthSize = 2

	fuaHeaderSize   = 2
	fuaStartBitmask = 0x80

	nuTypeBitmask   = 0x1F
	nuRefIdcBitmask = 0x60
)

var annexBStartCode = []byte{0x00, 0x00, 0x00, 0x01}

type H264AnnexB struct{}

func (p *H264AnnexB) Unmarshal(payload []byte) ([]byte, error) {
	if len(payload) == 0 {
		return nil, errors.New("empty h264 payload")
	}

	nuType := payload[0] & nuTypeBitmask
	switch {
	case nuType > 0 && nuType < 24:
		return append(annexBStartCode, payload...), nil
	case nuType == NALUTypeStapA:
		offset := stapAHeaderSize
		var result []byte
		for offset < len(payload) {
			if offset+1 >= len(payload) {
				return nil, errors.New("invalid h264 stap-a payload")
			}
			nuSize := int(binary.BigEndian.Uint16(payload[offset:]))
			offset += stapANALULengthSize
			if offset+nuSize >= len(payload) {
				return nil, errors.New("invalid h264 stap-a payload for data")
			}
			result = append(append(result, annexBStartCode...), payload[offset:offset+nuSize]...)
			offset += nuSize
		}
		return result, nil
	case nuType == NALUTypeFua:
		if len(payload) < fuaHeaderSize {
			return nil, errors.New("invalid h264 fua payload")
		}
		if payload[1]&fuaStartBitmask != 0 {
			nuRefIdc := payload[0] & nuRefIdcBitmask
			actualNuType := payload[1] & nuTypeBitmask
			return append(append(annexBStartCode, nuRefIdc|actualNuType), payload[fuaHeaderSize:]...), nil
		}
		return payload[fuaHeaderSize:], nil
	default:
		return nil, fmt.Errorf("unknown h264 with %v", nuType)
	}
}

type H264 struct {
	BaseInfo
}

func NewH264(bEarly bool, mimeType, volume, path, name string) (*H264, error) {
	media := &H264{}
	err := media.Init(false, bEarly, mimeType, volume, path, name)
	if err != nil {
		return media, err
	}
	return media, nil
}

func (f *H264) Write(packet []byte) ([]byte, int, int, error) {
	if !f.Mem() && f.IsDeleted() {
		return nil, 0, 0, nil
	}
	if !f.Mem() && !f.IsWriteable() {
		return nil, 0, 0, errors.New("the file is not writeable")
	}

	if len(packet) < RtpHeaderLength {
		return nil, 0, 0, fmt.Errorf("invalid rtp for h264 written: %v", len(packet))
	}
	// Record and parse
	f.Wrap(packet)
	payloadType := f.PayloadType()
	if payloadType < DynamicPayload {
		return nil, 0, 0, errors.New(fmt.Sprintf("invalid h264 payloadType: %v", payloadType))
	}

	// Resolve and Write
	frame, err := (&H264AnnexB{}).Unmarshal(f.Payload())
	if err != nil {
		return nil, 0, 0, err
	}
	_, err = f.MediaInfo.Write(frame)
	// Flush
	if f.IsEnough(VideoBufThreshold) {
		_, _ = f.Persist()
	}
	return frame, len(frame), 0, err
}

func (f *H264) Delete() error {
	err := f.MediaInfo.Close()
	err = os.Remove(f.FileName())
	return err
}

func (f *H264) Close() ([]byte, error) {
	if !f.Mem() && !f.IsWriteable() {
		return nil, nil
	}
	if !f.Mem() && f.IsDeleted() {
		return nil, f.Delete()
	}
	_, _ = f.Persist()
	return nil, f.MediaInfo.Close()
}