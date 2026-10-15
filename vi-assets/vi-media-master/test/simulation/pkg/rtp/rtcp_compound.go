package rtp

import (
	"encoding/binary"
	"fmt"
)

type CtrlPacket interface {
	fmt.Stringer
	Unmarshal([]byte) error
}

type CompoundCtrlPacket struct {
	packets []CtrlPacket
}

func (p *CompoundCtrlPacket) Unmarshal(raw []byte) error {
	packets := make([]CtrlPacket, 0)
	for len(raw) != 0 {
		packet, processed, err := Unmarshal(raw)
		if err != nil {
			return err
		}
		packets = append(packets, packet)
		raw = raw[processed:]
	}
	err := Validate(packets)
	if err == nil {
		p.packets = packets
	}
	return err
}

func (p CompoundCtrlPacket) String() string {
	var out string
	for _, packet := range p.packets {
		out += packet.String()
	}
	return out
}

func Validate(packets []CtrlPacket) error {
	if len(packets) == 0 {
		return errEmptyCompound
	}
	if len(packets) < 2 {
		return errCompoundTooShort
	}
	// 第一个RTCP包必须是SenderReport或ReceiverReport
	switch packets[0].(type) {
	case *SenderReport, *ReceiverReport:
	default:
		return errBadFirstPacket
	}
	packets = packets[1:]
	for _, pkt := range packets {
		switch p := pkt.(type) {
		case *ReceiverReport:
			// 可以有多个RR，最多31个，超出需通过RRs进行传输
			continue
		case *SourceDescription:
			// 必须包含一个CNAME
			var cLen, iLen uint
			for chunk := p.Chunk(cLen); chunk != nil; chunk = p.Chunk(cLen) {
				size := uint(chunk.size)
				for iLen < size-4 {
					iType := chunk.ItemType(iLen)
					if iType == SDESEnd {
						break
					}
					if iType == SDESCNAME {
						return nil
					}
					iLen += uint(chunk.ItemLength(iLen)) + 2
				}
				cLen += uint(chunk.Len())
			}
			return errMissingCNAME
		default:
			// 其他RTCP包不允许出现在SDES之前
			return errPacketBeforeCNAME
		}
	}
	return errMissingCNAME
}

func Unmarshal(raw []byte) (CtrlPacket, int, error) {
	if len(raw) < rtcpHeaderLength {
		return nil, 0, errPacketTooShort
	}
	pLen := (binary.BigEndian.Uint16(raw[headerLenOffset:]) + 1) << 2
	if pLen > uint16(len(raw)) {
		return nil, 0, errPacketTooShort
	}
	pTyp := raw[headerPTOffset]
	pFmt := raw[headerSRCOffset] & srcBit
	raw = raw[:pLen]

	var p CtrlPacket
	switch pTyp {
	case RtcpSR:
		p = NewSenderReport()
	case RtcpRR:
		p = NewReceiverReport()
	case RtcpSDES:
		p = NewSourceDescription()
	case RtcpBYE:
		p = NewBye()
	case RtcpAPP:
		p = NewApp()
	case RtcpTransportSpecificFB:
		switch pFmt {
		case RtcpTLFmtNACK:
			p = NewNack()
		case RtcpTLFmtTMMBR:
			p = NewTMMBR()
		case RtcpTLFmtTMMBN:
			p = NewTMMBN()
		}
	case RtcpPayloadSpecificFB:
		switch pFmt {
		case RtcpPSFmtPLI:
			p = NewPLI()
		case RtcpPSFmtSLI:
			p = NewSLI()
		case RtcpPSFmtFIR:
			p = NewFIR()
		case RtcpPSFmtREMB:

		}
	case RtcpXR:

	}
	if p == nil {
		return nil, 0, errUnknownPacketType
	}
	if err := p.Unmarshal(raw); err != nil {
		return nil, 0, err
	}
	return p, int(pLen), nil
}