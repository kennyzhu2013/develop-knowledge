package record

import (
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"fmt"
)

const (
	RtpHeaderLength = 12

	// DynamicPayload >= 96, dynamic == telephone-event
	DynamicPayload = 96

	verOffset  = 0
	markOffset = 1
	seqOffset  = 2
	tsOffset   = 4
	ssrcOffset = 8

	paddingBit   = 0x20
	cSrcCntBit   = 0x0F
	extensionBit = 0x10

	markerBit      = 0x80
	payloadTypeBit = 0x7F
)

type Packet struct {
	buf  []byte
	pad  int
	size int
}

func (p *Packet) Wrap(buf []byte) {
	p.buf = buf
	p.size = len(buf)
}

func (p *Packet) PayloadType() uint8 {
	return p.buf[markOffset] & payloadTypeBit
}

func (p *Packet) Sequence() uint16 {
	return binary.BigEndian.Uint16(p.buf[seqOffset:])
}

func (p *Packet) Timestamp() uint32 {
	return binary.BigEndian.Uint32(p.buf[tsOffset:])
}

func (p *Packet) Ssrc() uint32 {
	return binary.BigEndian.Uint32(p.buf[ssrcOffset:])
}

func (p *Packet) Padding() bool {
	return p.buf[verOffset]&paddingBit == paddingBit
}

func (p *Packet) Marker() bool {
	return p.buf[markOffset]&markerBit == markerBit
}

func (p *Packet) CSrcCount() uint8 {
	return p.buf[verOffset] & cSrcCntBit
}

func (p *Packet) CSrcList() []uint32 {
	ccList := make([]uint32, p.CSrcCount())
	for i := range ccList {
		ccList[i] = binary.BigEndian.Uint32(p.buf[RtpHeaderLength+(i<<2):])
	}
	return ccList
}

func (p *Packet) ExtensionBit() bool {
	return p.buf[verOffset]&extensionBit == extensionBit
}

func (p *Packet) ExtensionLength() int {
	if !p.ExtensionBit() {
		return 0
	}
	return (int(binary.BigEndian.Uint16(p.buf[int16((p.CSrcCount()<<2)+RtpHeaderLength)+2:])) + 1) << 2
}

func (p *Packet) Extension() []byte {
	if !p.ExtensionBit() {
		return nil
	}
	offset := int(p.CSrcCount()<<2) + RtpHeaderLength
	return p.buf[offset : offset+p.ExtensionLength()]
}

func (p *Packet) Payload() []byte {
	pad := 0
	offset := int(p.CSrcCount()<<2) + RtpHeaderLength + p.ExtensionLength()
	if p.Padding() {
		pad = int(p.buf[p.size-1])
	}
	return p.buf[offset : p.size-pad]
}

func (p *Packet) Size() int {
	return p.size
}

func (p *Packet) String() string {
	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("  fixed header dump:   %s\n", hex.EncodeToString(p.buf[:RtpHeaderLength])))
	buf.WriteString(fmt.Sprintf("    Version:           %d\n", (p.buf[0]&0xc0)>>6))
	buf.WriteString(fmt.Sprintf("    Padding:           %t\n", p.Padding()))
	buf.WriteString(fmt.Sprintf("    Extension:         %t\n", p.ExtensionBit()))
	buf.WriteString(fmt.Sprintf("    Contributing SRCs: %d\n", p.CSrcCount()))
	buf.WriteString(fmt.Sprintf("    Marker:            %t\n", p.Marker()))
	buf.WriteString(fmt.Sprintf("    Payload type:      %d (0x%x)\n", p.PayloadType(), p.PayloadType()))
	buf.WriteString(fmt.Sprintf("    Sequence number:   %d (0x%x)\n", p.Sequence(), p.Sequence()))
	buf.WriteString(fmt.Sprintf("    Timestamp:         %d (0x%x)\n", p.Timestamp(), p.Timestamp()))
	buf.WriteString(fmt.Sprintf("    SSRC:              %d (0x%x)\n", p.Ssrc(), p.Ssrc()))

	if p.CSrcCount() > 0 {
		ccList := p.CSrcList()
		buf.WriteString(fmt.Sprintf("  CSRC list:\n"))
		for i, v := range ccList {
			buf.WriteString(fmt.Sprintf("      %d: %d (0x%x)\n", i, v, v))
		}
	}

	if p.ExtensionBit() {
		extLen := p.ExtensionLength()
		buf.WriteString(fmt.Sprintf("  Extentsion length: %d\n", extLen))
		offsetExt := RtpHeaderLength + int(p.CSrcCount()<<2)
		buf.WriteString(fmt.Sprintf("    extension: %s\n", hex.EncodeToString(p.buf[offsetExt:offsetExt+extLen])))
	}
	buf.WriteString(fmt.Sprintf("  Payload length: %d\n", p.size-RtpHeaderLength+int(p.CSrcCount()<<2)+p.ExtensionLength()))
	return buf.String()
}