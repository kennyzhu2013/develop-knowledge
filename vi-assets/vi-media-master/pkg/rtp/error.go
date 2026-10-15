package rtp

import (
	"bytes"
	"errors"
)

var (
	errReachedBindingLimit = errors.New("binding reaches the upper limit")
	errEmptyCompound       = errors.New("rtcp: empty compound packet")
	errBadFirstPacket      = errors.New("rtcp: first packet in compound must be SR or RR")
	errMissingCNAME        = errors.New("rtcp: compound packet missing SourceDescription with CNAME")
	errPacketBeforeCNAME   = errors.New("rtcp: feedback packet seen before CNAME")
	errPacketTooShort      = errors.New("rtcp: packet too short")
	errCompoundTooShort    = errors.New("rtcp: compound packet too short")
	errWrongType           = errors.New("rtcp: wrong packet type")
	errInvalidHeader       = errors.New("rtcp: invalid header")
	errInvalidLength       = errors.New("rtcp: invalid length")
	errUnknownPacketType   = errors.New("rtcp: unknown packet type")
)

type MultiError []error

func (e MultiError) Error() string {
	var buffer bytes.Buffer
	for _, err := range e {
		buffer.WriteString(err.Error())
		buffer.WriteString(".")
	}
	return buffer.String()
}