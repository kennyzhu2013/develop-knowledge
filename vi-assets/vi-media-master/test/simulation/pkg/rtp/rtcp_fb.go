package rtp

import (
	"encoding/binary"
	"fmt"
)

const (
	// RtcpTransportSpecificFB 传输层反馈信息，即通用RTP反馈信息
	RtcpTransportSpecificFB uint8 = 205
	// RtcpPayloadSpecificFB 载荷指定反馈信息
	RtcpPayloadSpecificFB uint8 = 206
)

const (
	RtcpTLFmtNACK  uint8 = 1
	RtcpTLFmtTMMBR uint8 = 3
	RtcpTLFmtTMMBN uint8 = 4
	RtcpTLFmtRRR   uint8 = 5

	RtcpPSFmtPLI  uint8 = 1
	RtcpPSFmtSLI  uint8 = 2
	RtcpPSFmtFIR  uint8 = 4
	RtcpPSFmtTSTR uint8 = 5
	RtcpPSFmtTSTN uint8 = 6
	RtcpPSFmtREMB uint8 = 15
)

const (
	rtcpFBHeader = 12

	nackPidLength  = 2
	nackFCILength  = 4
	minNACKLength  = 16
	tmmbrFCILength = 8
	minTMMBRLength = 20
	tmmbnFCILength = 8
	minTMMBNLength = 12
	sliFCILength   = 4
	minSLILength   = 16
	firFCILength   = 8
	minFIRLength   = 20

	fbPFmtOffset   = 0
	fbPTOffset     = 1
	fbLengthOffset = 2
	fbSsrcOffset   = 4
	fbMSsrcOffset  = 8
	fbFCIOffset    = 12

	nackBLPOffset = 14

	tmmbrMxTbrExpOffset = 16
	tmmbnMxTbrExpOffset = 16
)

/**
 * 1、Transport layer FB
 * 2、Payload-specific FB
 * 3、Application layer FB
 */

/**
 *				0               1               2               3
 *		0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|V=2|P|   FMT   |       PT      |          length               |
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|                   SSRC of packet sender                       |
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|                   SSRC of media source                        |
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		:             Feedback Control Information (FCI)                :
 *		:                                                               :
 */

type FBRtcpPacket struct {
	buf  []byte
	pad  int
	size int
}

func (p *FBRtcpPacket) Padding() bool {
	return p.buf[fbPFmtOffset]&pBit == pBit
}

func (p *FBRtcpPacket) SetPadding(padding bool, pad int) {
	if padding {
		p.buf[fbPFmtOffset] |= pBit
		if pad == 0 {
			p.pad = 4
		} else {
			p.pad = pad
		}
	} else {
		p.buf[fbPFmtOffset] &^= pBit
		p.pad = 0
	}
}

// Fmt 指示了当前RTCP-FB包相对PayloadType的子类型
func (p *FBRtcpPacket) Fmt() uint8 {
	return p.buf[fbPFmtOffset] & srcBit
}

func (p *FBRtcpPacket) SetFmt(fmt uint8) {
	p.buf[fbPFmtOffset] &^= srcBit
	p.buf[fbPFmtOffset] |= fmt & srcBit
}

func (p *FBRtcpPacket) PayloadType() uint8 {
	return p.buf[fbPTOffset]
}

func (p *FBRtcpPacket) SetPayloadType(pt uint8) {
	p.buf[fbPTOffset] = pt
}

// Length RTCP包长度-1，包含了头部和填充字节，单位为4字节
func (p *FBRtcpPacket) Length() uint16 {
	return (binary.BigEndian.Uint16(p.buf[fbLengthOffset:]) + 1) << 2
}

func (p *FBRtcpPacket) SetLength(length uint16) {
	binary.BigEndian.PutUint16(p.buf[fbLengthOffset:], (length>>2)-1)
}

func (p *FBRtcpPacket) Ssrc() uint32 {
	return binary.BigEndian.Uint32(p.buf[fbSsrcOffset:])
}

func (p *FBRtcpPacket) SetSsrc(ssrc uint32) {
	binary.BigEndian.PutUint32(p.buf[fbSsrcOffset:], ssrc)
}

func (p *FBRtcpPacket) MediaSsrc() uint32 {
	return binary.BigEndian.Uint32(p.buf[fbMSsrcOffset:])
}

func (p *FBRtcpPacket) SetMediaSsrc(ssrc uint32) {
	binary.BigEndian.PutUint32(p.buf[fbMSsrcOffset:], ssrc)
}

/**
 *  			0               1               2               3
 *		0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|            PID                |             BLP               |
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

// Nack 用于指示一个或多个RTP包的丢失
// 至少包含一个NACK的FCI
type Nack struct {
	FBRtcpPacket
}

func NewNack() *Nack {
	buf := make([]byte, defaultBufSize)
	return &Nack{FBRtcpPacket: FBRtcpPacket{buf: buf, size: minNACKLength}}
}

// Pid 指示一个丢失的RTP包的序列号
func (n *Nack) Pid(index uint) uint16 {
	if uint((n.size-n.pad-rtcpFBHeader)>>2) > index {
		return binary.BigEndian.Uint16(n.buf[fbFCIOffset+index*nackFCILength:])
	}
	return 0
}

func (n *Nack) BLP(index uint) uint16 {
	if uint((n.size-n.pad-rtcpFBHeader)>>2) > index {
		return binary.BigEndian.Uint16(n.buf[nackBLPOffset+index*nackFCILength:])
	}
	return 0
}

func (n *Nack) SetFCI(pid ...uint16) {
	if len(pid) == 0 {
		return
	}
	offset := fbFCIOffset
	cPid, cBLP := pid[0], uint16(0)
	binary.BigEndian.PutUint16(n.buf[offset:], cPid)
	for i := 1; i < len(pid); i++ {
		if pid[i]-cPid > 16 {
			offset += nackFCILength
			binary.BigEndian.PutUint16(n.buf[offset:], pid[i])
			n.size += nackFCILength
			continue
		}
		cBLP |= 1 << (pid[i] - cPid - 1)
		binary.BigEndian.PutUint16(n.buf[offset+nackPidLength:], cBLP)
	}
}

func (n *Nack) Unmarshal(raw []byte) error {
	if len(raw) < minNACKLength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpTransportSpecificFB || raw[headerSRCOffset]&srcBit != RtcpTLFmtNACK {
		return errWrongType
	}
	var pad int
	if raw[headerSRCOffset]&pBit == pBit {
		pad = int(raw[len(raw)-1])
		if pad == 0 {
			return errInvalidHeader
		}
	}
	if (len(raw)-pad)%4 != 0 {
		return errWrongType
	}
	n.size = copy(n.buf, raw[:len(raw)-pad])
	n.pad = pad
	return nil
}

func (n *Nack) String() string {
	out := fmt.Sprintf("TransportLayerNack from %x\n", n.Ssrc())
	out += fmt.Sprintf("\tMedia Ssrc %x\n", n.MediaSsrc())
	out += "\tID\tLostPackets\n"
	for i, nums := uint(0), uint((n.size-n.pad-rtcpFBHeader)>>2); i < nums; i++ {
		out += fmt.Sprintf("\t%d\t%b\n", n.Pid(i), n.BLP(i))
	}
	return out
}

/**
 *  			0               1               2               3
 *		0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|                       SSRC of source                          |
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		| MxTBR Exp |          MxTBR Mantissa         |Measured Overhead|
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

// TMMBR 用于流控，请求对端在指定的最大传输比特率下传输RTP，最大传输比特率 = 系数*2^指数
// 至少包含一个TMMBR的FCI
type TMMBR struct {
	FBRtcpPacket
}

func NewTMMBR() *TMMBR {
	buf := make([]byte, defaultBufSize)
	return &TMMBR{FBRtcpPacket: FBRtcpPacket{buf: buf, size: minTMMBRLength}}
}

func (t *TMMBR) PeerSsrc(index uint) uint32 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return binary.BigEndian.Uint32(t.buf[fbFCIOffset+index*tmmbrFCILength:])
	}
	return 0
}

func (t *TMMBR) SetPeerSsrc(index uint, ssrc uint32) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		binary.BigEndian.PutUint32(t.buf[fbFCIOffset+index*tmmbrFCILength:], ssrc)
	}
}

// MxTbrExp 指数
func (t *TMMBR) MxTbrExp(index uint) uint8 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return t.buf[tmmbrMxTbrExpOffset+index*tmmbrFCILength] >> 2
	}
	return 0
}

func (t *TMMBR) SetMxTbrExp(index uint, mxTbrExp uint8) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		t.buf[tmmbrMxTbrExpOffset+index*tmmbrFCILength] = (t.buf[tmmbrMxTbrExpOffset+index*tmmbrFCILength] & 0x03) | (mxTbrExp << 2)
	}
}

// MxTbrMantissa 系数
func (t *TMMBR) MxTbrMantissa(index uint) uint32 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return (binary.BigEndian.Uint32(t.buf[tmmbrMxTbrExpOffset+index*tmmbrFCILength:]) & 0x03FFFFFF) >> 9
	}
	return 0
}

func (t *TMMBR) SetMxTbrMantissa(index uint, mxTbrMantissa uint32) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		binary.BigEndian.PutUint32(t.buf[tmmbrMxTbrExpOffset+index*tmmbrFCILength:], binary.BigEndian.Uint32(t.buf[tmmbrMxTbrExpOffset+index*tmmbrFCILength:])&0xFC0001FF|(mxTbrMantissa<<9))
	}
}

// MeasuredOverhead 平均开销
func (t *TMMBR) MeasuredOverhead(index uint) uint16 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return (uint16(t.buf[tmmbrMxTbrExpOffset+2+index*tmmbrFCILength]&0x01) << 8) | uint16(t.buf[tmmbrMxTbrExpOffset+3+index*tmmbrFCILength])
	}
	return 0
}

func (t *TMMBR) SetMeasuredOverhead(index uint, measuredOverhead uint16) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		t.buf[tmmbrMxTbrExpOffset+3+index*tmmbrFCILength] = byte(measuredOverhead)
		t.buf[tmmbrMxTbrExpOffset+2+index*tmmbrFCILength] = t.buf[tmmbrMxTbrExpOffset+2]&0xFE | byte(measuredOverhead>>8)
	}
}

func (t *TMMBR) Unmarshal(raw []byte) error {
	if len(raw) < minTMMBRLength {
		return errWrongType
	}
	if raw[headerPTOffset] != RtcpTransportSpecificFB || raw[headerSRCOffset]&srcBit != RtcpTLFmtTMMBR {
		return errWrongType
	}
	var pad int
	if raw[headerSRCOffset]&pBit == pBit {
		pad = int(raw[len(raw)-1])
		if pad == 0 {
			return errInvalidHeader
		}
	}
	if (len(raw)-pad-rtcpFBHeader)%tmmbrFCILength != 0 {
		return errWrongType
	}
	t.size = copy(t.buf, raw[:len(raw)-pad])
	t.pad = pad
	return nil
}

func (t *TMMBR) String() string {
	out := fmt.Sprintf("TMMBR from %x\n", t.Ssrc())
	out += fmt.Sprintf("\tMedia Ssrc %x\n", t.MediaSsrc())
	out += "\tPeer Ssrc\tMxTbrExp\tMxTbrMantissa\n"
	for i, nums := uint(0), uint((t.size-t.pad-rtcpFBHeader)>>3); i < nums; i++ {
		out += fmt.Sprintf("\t%x\t%d\t%d\n", t.PeerSsrc(i), t.MxTbrExp(i), t.MxTbrMantissa(i))
	}
	return out
}

/**
 *  			0               1               2               3
 *		0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|                       SSRC of source                          |
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		| MxTBR Exp |          MxTBR Mantissa         |Measured Overhead|
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

// TMMBN 用于对TMMBR的应答
type TMMBN struct {
	FBRtcpPacket
}

func NewTMMBN() *TMMBN {
	buf := make([]byte, defaultBufSize)
	return &TMMBN{FBRtcpPacket: FBRtcpPacket{buf: buf, size: minTMMBNLength}}
}

func (t *TMMBN) PeerSsrc(index uint) uint32 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return binary.BigEndian.Uint32(t.buf[fbFCIOffset+index*tmmbnFCILength:])
	}
	return 0
}

func (t *TMMBN) SetPeerSsrc(index uint, ssrc uint32) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		binary.BigEndian.PutUint32(t.buf[fbFCIOffset+index*tmmbnFCILength:], ssrc)
	}
}

// MxTbrExp 指数
func (t *TMMBN) MxTbrExp(index uint) uint8 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return t.buf[tmmbnMxTbrExpOffset+index*tmmbnFCILength] >> 2
	}
	return 0
}

func (t *TMMBN) SetMxTbrExp(index uint, mxTbrExp uint8) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		t.buf[tmmbnMxTbrExpOffset+index*tmmbnFCILength] = (t.buf[tmmbnMxTbrExpOffset+index*tmmbnFCILength] & 0x03) | (mxTbrExp << 2)
	}
}

// MxTbrMantissa 系数
func (t *TMMBN) MxTbrMantissa(index uint) uint32 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return (binary.BigEndian.Uint32(t.buf[tmmbnMxTbrExpOffset+index*tmmbnFCILength:]) & 0x03FFFFFF) >> 9
	}
	return 0
}

func (t *TMMBN) SetMxTbrMantissa(index uint, mxTbrMantissa uint32) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		binary.BigEndian.PutUint32(t.buf[tmmbnMxTbrExpOffset+index*tmmbnFCILength:], binary.BigEndian.Uint32(t.buf[tmmbnMxTbrExpOffset+index*tmmbnFCILength:])&0xFC0001FF|(mxTbrMantissa<<9))
	}
}

// MeasuredOverhead 平均开销
func (t *TMMBN) MeasuredOverhead(index uint) uint16 {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		return (uint16(t.buf[tmmbnMxTbrExpOffset+2+index*tmmbnFCILength]&0x01) << 8) | uint16(t.buf[tmmbnMxTbrExpOffset+3+index*tmmbnFCILength])
	}
	return 0
}

func (t *TMMBN) SetMeasuredOverhead(index uint, measuredOverhead uint16) {
	if uint((t.size-t.pad-rtcpFBHeader)>>3) > index {
		t.buf[tmmbnMxTbrExpOffset+3+index*tmmbnFCILength] = byte(measuredOverhead)
		t.buf[tmmbnMxTbrExpOffset+2+index*tmmbnFCILength] = t.buf[tmmbnMxTbrExpOffset+2]&0xFE | byte(measuredOverhead>>8)
	}
}

func (t *TMMBN) Unmarshal(raw []byte) error {
	if len(raw) < minTMMBNLength {
		return errWrongType
	}
	if raw[headerPTOffset] != RtcpTransportSpecificFB || raw[headerSRCOffset]&srcBit != RtcpTLFmtTMMBN {
		return errWrongType
	}
	var pad int
	if raw[headerSRCOffset]&pBit == pBit {
		pad = int(raw[len(raw)-1])
		if pad == 0 {
			return errInvalidHeader
		}
	}
	if (len(raw)-pad-rtcpFBHeader)%tmmbnFCILength != 0 {
		return errWrongType
	}
	t.size = copy(t.buf, raw[:len(raw)-pad])
	t.pad = pad
	return nil
}

func (t *TMMBN) String() string {
	out := fmt.Sprintf("TMMBN from %x\n", t.Ssrc())
	out += fmt.Sprintf("\tMedia Ssrc %x\n", t.MediaSsrc())
	out += "\tPeer Ssrc\tMxTbrExp\tMxTbrMantissa\n"
	for i, nums := uint(0), uint((t.size-t.pad-rtcpFBHeader)>>3); i < nums; i++ {
		out += fmt.Sprintf("\t%x\t%d\t%d\n", t.PeerSsrc(i), t.MxTbrExp(i), t.MxTbrMantissa(i))
	}
	return out
}

/**
 * PLI 用于向发送端表明关键帧的丢失
 */

type PLI struct {
	FBRtcpPacket
}

func NewPLI() *PLI {
	buf := make([]byte, defaultBufSize)
	return &PLI{FBRtcpPacket: FBRtcpPacket{buf: buf, size: rtcpFBHeader}}
}

func (p *PLI) Unmarshal(raw []byte) error {
	if len(raw) < rtcpFBHeader {
		return errWrongType
	}
	if raw[headerPTOffset] != RtcpPayloadSpecificFB || raw[headerSRCOffset]&srcBit != RtcpPSFmtPLI {
		return errWrongType
	}
	var pad int
	if raw[headerSRCOffset]&pBit == pBit {
		pad = int(raw[len(raw)-1])
		if pad == 0 {
			return errInvalidHeader
		}
	}
	if len(raw)-pad != rtcpFBHeader {
		return errWrongType
	}
	p.size = copy(p.buf, raw[:len(raw)-pad])
	p.pad = pad
	return nil
}

func (p *PLI) String() string {
	return fmt.Sprintf("PictureLossIndication %x %x\n", p.Ssrc(), p.MediaSsrc())
}

/**
 *  			0               1               2               3
 *		0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 		|            First        |        Number           | PictureID |
 * 		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

// SLI 用于向发送端表明一个或多个连续宏块的丢失或损坏
// 至少包含一个SLI的FCI
type SLI struct {
	FBRtcpPacket
}

func NewSLI() *SLI {
	buf := make([]byte, defaultBufSize)
	return &SLI{FBRtcpPacket: FBRtcpPacket{buf: buf, size: rtcpFBHeader}}
}

// First 第一个丢失的宏块的编号
// 图片左上角的宏块编号为1，每个宏块的编号按光栅扫描顺序从左到右，然后从上到下递增
func (s *SLI) First(index uint) uint16 {
	if uint((s.size-s.pad-rtcpFBHeader)>>2) > index {
		return uint16(binary.BigEndian.Uint32(s.buf[fbFCIOffset+index*nackFCILength:])>>19) & 0x1FFF
	}
	return 0
}

func (s *SLI) SetFirst(index uint, first uint16) {
	if uint((s.size-s.pad-rtcpFBHeader)>>2) > index {
		s.buf[fbFCIOffset+index*sliFCILength] = byte(first >> 5)
		s.buf[fbFCIOffset+1+index*sliFCILength] = (s.buf[fbFCIOffset+1+index*sliFCILength] & 0x07) | (byte(first) << 3)
	}
}

// Number 丢失的宏块数
func (s *SLI) Number(index uint) uint16 {
	if uint((s.size-s.pad-rtcpFBHeader)>>2) > index {
		return uint16(binary.BigEndian.Uint32(s.buf[fbFCIOffset+index*nackFCILength:])>>6) & 0x1FFF
	}
	return 0
}

func (s *SLI) SetNumber(index uint, number uint16) {
	if uint((s.size-s.pad-rtcpFBHeader)>>2) > index {
		s.buf[fbFCIOffset+1+index*sliFCILength] = (s.buf[fbFCIOffset+1+index*sliFCILength] & 0xF8) | byte(number>>10)
		s.buf[fbFCIOffset+2+index*sliFCILength] = byte(number >> 2)
		s.buf[fbFCIOffset+3+index*sliFCILength] = (s.buf[fbFCIOffset+3+index*sliFCILength] & 0x3F) | (byte(number) << 6)
	}
}

// PictureID 编解码器特定标识符的六个最低有效位，用于引用发生宏块丢失的图片
func (s *SLI) PictureID(index uint) uint8 {
	if uint((s.size-s.pad-rtcpFBHeader)>>2) > index {
		return s.buf[fbFCIOffset+3+index*nackFCILength] & 0x3F
	}
	return 0
}

func (s *SLI) SetPictureID(index uint, pictureId uint8) {
	if uint((s.size-s.pad-rtcpFBHeader)>>2) > index {
		s.buf[fbFCIOffset+3+index*sliFCILength] = (s.buf[fbFCIOffset+3+index*sliFCILength] & 0xC0) | (pictureId & 0x3F)
	}
}

func (s *SLI) AddFCI(first, number uint16, pictureId uint8) {
	s.buf[s.size] = byte(first >> 5)
	s.buf[s.size+1] = (byte(first) << 3) | byte(number>>10)
	s.buf[s.size+2] = byte(number >> 2)
	s.buf[s.size+3] = (byte(number) << 6) | (pictureId & 0x3F)
	s.size += sliFCILength
}

func (s *SLI) Unmarshal(raw []byte) error {
	if len(raw) < minSLILength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpPayloadSpecificFB || raw[headerSRCOffset]&srcBit != RtcpPSFmtSLI {
		return errWrongType
	}
	var pad int
	if raw[headerSRCOffset]&pBit == pBit {
		pad = int(raw[len(raw)-1])
		if pad == 0 {
			return errInvalidHeader
		}
	}
	if (len(raw)-pad)%4 != 0 {
		return errWrongType
	}
	s.size = copy(s.buf, raw[:len(raw)-pad])
	s.pad = pad
	return nil
}

func (s *SLI) String() string {
	out := fmt.Sprintf("SliceLossIndication from %x\n", s.Ssrc())
	out += fmt.Sprintf("\tMedia Ssrc %x\n", s.MediaSsrc())
	out += "\tFirst\tNumber\tPictureID\n"
	for i, nums := uint(0), uint((s.size-s.pad-rtcpFBHeader)>>2); i < nums; i++ {
		out += fmt.Sprintf("\t%d\t%d\t%d\n", s.First(i), s.Number(i), s.PictureID(i))
	}
	return out
}

/**
 *  			0               1               2               3
 *		0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|                       SSRC of source                          |
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *		|   Sequence    |          			 Reserved					|
 *		+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

// FIR 请求关键帧
// 至少包含一个FIR的FCI
// 与PLI类似，FIR用于接收端需要关键帧进行解码的情况，而PLI用于接收端检测到图像丢失或者解码错误的情况
type FIR struct {
	FBRtcpPacket
}

func NewFIR() *FIR {
	buf := make([]byte, defaultBufSize)
	return &FIR{FBRtcpPacket: FBRtcpPacket{buf: buf, size: rtcpFBHeader}}
}

func (r *FIR) PeerSsrc(index uint) uint32 {
	if uint((r.size-r.pad-rtcpFBHeader)>>3) > index {
		return binary.BigEndian.Uint32(r.buf[fbFCIOffset+index*firFCILength:])
	}
	return 0
}

func (r *FIR) SetPeerSsrc(index uint, ssrc uint32) {
	if uint((r.size-r.pad-rtcpFBHeader)>>3) > index {
		binary.BigEndian.PutUint32(r.buf[fbFCIOffset+index*firFCILength:], ssrc)
	}
}

// Sequence 命令序列号，初始值随机
// 对于每个新命令，序列号应增加1mod256
func (r *FIR) Sequence(index uint) uint8 {
	if uint((r.size-r.pad-rtcpFBHeader)>>3) > index {
		return r.buf[fbFCIOffset+4]
	}
	return 0
}

func (r *FIR) SetSequence(index uint, seq uint8) {
	if uint((r.size-r.pad-rtcpFBHeader)>>3) > index {
		r.buf[fbFCIOffset+4+index*firFCILength] = seq
	}
}

func (r *FIR) AddFCI(ssrc uint32, seq uint8) {
	binary.BigEndian.PutUint32(r.buf[r.size:], ssrc)
	r.buf[r.size+4] = seq
	r.size += firFCILength
}

func (r *FIR) Unmarshal(raw []byte) error {
	if len(raw) < minFIRLength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpPayloadSpecificFB || raw[headerSRCOffset]&srcBit != RtcpPSFmtFIR {
		return errWrongType
	}
	var pad int
	if raw[headerSRCOffset]&pBit == pBit {
		pad = int(raw[len(raw)-1])
		if pad == 0 {
			return errInvalidHeader
		}
	}
	if (len(raw)-pad-rtcpFBHeader)%firFCILength != 0 {
		return errWrongType
	}
	r.size = copy(r.buf, raw[:len(raw)-pad])
	r.pad = pad
	return nil
}

func (r *FIR) String() string {
	out := fmt.Sprintf("FullIntraRequest from %x\n", r.Ssrc())
	out += fmt.Sprintf("\tMedia Ssrc %x\n", r.MediaSsrc())
	out += "\tPeer SSRC\tSequence\n"
	for i, nums := uint(0), uint((r.size-r.pad-rtcpFBHeader)>>3); i < nums; i++ {
		out += fmt.Sprintf("\t%x\t%d\n", r.PeerSsrc(i), r.Sequence(i))
	}
	return out
}