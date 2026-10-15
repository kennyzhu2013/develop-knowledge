package rtp

import (
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	mRand "math/rand"
	"sync"
	"time"
)

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |            contributing source (CSRC) identifiers             |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      defined by profile       |            length			   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |            				payload             			   |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |               		padding           		 |      length     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

const (
	HeaderLength = 12

	defaultBufSize = 1600
	maxCSrc        = 16
)

const (
	verPXOffset  = 0
	markPtOffset = 1
	seqOffset    = 2
	tsOffset     = 4
	ssrcOffset   = 8

	extensionBit = 0x10
	paddingBit   = 0x20
	markerBit    = 0x80
	ptBit        = 0x7F
	ccBit        = 0x0F
)

var pool sync.Pool

func init() {
	pool.New = func() interface{} {
		buf := make([]byte, defaultBufSize)
		buf[verPXOffset] = 0x80
		return &Packet{buf: buf, size: HeaderLength}
	}
}

type Packet struct {
	buf  []byte
	pad  int
	size int
}

func NewPacket() *Packet {
	return pool.Get().(*Packet)
}

func newPacket(seq uint16, marker bool, timestamp uint32) *Packet {
	packet := NewPacket()
	packet.SetMarker(marker)
	packet.SetSequence(seq)
	packet.SetTimestamp(timestamp)
	return packet
}

func FreePacket(p *Packet) {
	p.buf[verPXOffset] = 0x80
	p.buf[markPtOffset] = 0
	p.size = HeaderLength
	p.pad = 0
	pool.Put(p)
}

func (p *Packet) PayloadType() uint8 {
	return p.buf[markPtOffset] & ptBit
}

func (p *Packet) SetPayloadType(pt uint8) {
	p.buf[markPtOffset] &^= ptBit
	p.buf[markPtOffset] |= pt & ptBit
}

func (p *Packet) Sequence() uint16 {
	return binary.BigEndian.Uint16(p.buf[seqOffset:])
}

func (p *Packet) SetSequence(seq uint16) {
	binary.BigEndian.PutUint16(p.buf[seqOffset:], seq)
}

func (p *Packet) Timestamp() uint32 {
	return binary.BigEndian.Uint32(p.buf[tsOffset:])
}

func (p *Packet) SetTimestamp(timestamp uint32) {
	binary.BigEndian.PutUint32(p.buf[tsOffset:], timestamp)
}

func (p *Packet) Ssrc() uint32 {
	return binary.BigEndian.Uint32(p.buf[ssrcOffset:])
}

func (p *Packet) SetSSRC(ssrc uint32) {
	binary.BigEndian.PutUint32(p.buf[ssrcOffset:], ssrc)
}

func (p *Packet) Padding() bool {
	return p.buf[verPXOffset]&paddingBit == paddingBit
}

func (p *Packet) SetPadding(padding bool, pad int) {
	if padding {
		p.buf[verPXOffset] |= paddingBit
		if pad == 0 {
			p.pad = 4
		} else {
			p.pad = pad
		}
	} else {
		p.buf[verPXOffset] &^= paddingBit
		p.pad = 0
	}
}

func (p *Packet) Marker() bool {
	return p.buf[markPtOffset]&markerBit == markerBit
}

func (p *Packet) SetMarker(mark bool) {
	if mark {
		p.buf[markPtOffset] |= markerBit
	} else {
		p.buf[markPtOffset] &^= markerBit
	}
}

func (p *Packet) CsrcCount() uint8 {
	return p.buf[verPXOffset] & ccBit
}

func (p *Packet) CsrcList() []uint32 {
	ccList := make([]uint32, p.CsrcCount())
	for i := range ccList {
		ccList[i] = binary.BigEndian.Uint32(p.buf[HeaderLength+(i<<2):])
	}
	return ccList
}

func (p *Packet) SetCsrcList(csrc []uint32) {
	if len(csrc) > maxCSrc {
		return
	}
	offsetOld := int(p.CsrcCount()<<2) + HeaderLength
	offsetNew := (len(csrc) << 2) + HeaderLength
	// 计算新数据包大小
	newSize := offsetNew + (p.size - offsetOld)
	if newSize > cap(p.buf) {
		return
	}

	// 重新定位载荷
	copy(p.buf[offsetNew:], p.buf[offsetOld:p.size])
	// 添加覆盖原CSRC
	for i, cc := range csrc {
		binary.BigEndian.PutUint32(p.buf[HeaderLength+(i<<2):], cc)
	}

	// 设置CC Count
	p.buf[verPXOffset] &^= ccBit
	p.buf[verPXOffset] |= byte(len(csrc) & ccBit)
	// 设置新数据包大小
	p.size = newSize
}

func (p *Packet) ExtensionBit() bool {
	return p.buf[verPXOffset]&extensionBit == extensionBit
}

func (p *Packet) ExtensionLength() int {
	if !p.ExtensionBit() {
		return 0
	}
	return (int(binary.BigEndian.Uint16(p.buf[int16((p.CsrcCount()<<2)+HeaderLength)+2:])) + 1) << 2
}

func (p *Packet) Extension() []byte {
	if !p.ExtensionBit() {
		return nil
	}
	offset := int(p.CsrcCount()<<2) + HeaderLength
	return p.buf[offset : offset+p.ExtensionLength()]
}

// SetExtension RFC 3550 or RFC 5258
// 扩展头，长度为uint32的整数倍，长度字段为ext[2:4]
func (p *Packet) SetExtension(ext []byte) {
	if len(ext)&(len(ext)-1) != 0 {
		return
	}
	l := 0
	if len(ext) > 0 {
		l = (int(binary.BigEndian.Uint16(ext[2:])) + 1) << 2
	}
	if len(ext) != l {
		return
	}
	offsetExt := int(p.CsrcCount()<<2) + HeaderLength
	offsetOld := offsetExt + p.ExtensionLength()
	offsetNew := offsetExt + l
	// 计算新数据包大小
	newSize := offsetNew + (p.size - offsetOld)
	if newSize > cap(p.buf) {
		return
	}

	// 重新定位载荷
	copy(p.buf[offsetNew:], p.buf[offsetOld:p.size])
	// 添加覆盖原Extension
	copy(p.buf[offsetExt:], ext)

	// 设置扩展头标记
	if l == 0 {
		p.buf[verPXOffset] &^= extensionBit
	} else {
		p.buf[verPXOffset] |= extensionBit
	}
	// 设置新数据包大小
	p.size = newSize
}

func (p *Packet) Payload() []byte {
	pad := 0
	offset := int(p.CsrcCount()<<2) + HeaderLength + p.ExtensionLength()
	if p.Padding() {
		pad = int(p.buf[p.size-1])
	}
	return p.buf[offset : p.size-pad]
}

func (p *Packet) SetPayload(payload []byte) {
	offset := int(p.CsrcCount()<<2) + HeaderLength + p.ExtensionLength()
	if (offset + len(payload) + p.pad) > cap(p.buf) {
		return
	}

	// 载荷拷贝
	copy(p.buf[offset:], payload)
	p.size = offset + len(payload)

	// Padding填充
	if p.Padding() && len(payload) > 0 {
		for i := 0; i < p.pad-1; i++ {
			p.buf[p.size] = 0
			p.size++
		}
		p.buf[p.size] = byte(p.pad)
		p.size++
	}
}

func (p *Packet) Copy() []byte {
	data := make([]byte, p.size)
	copy(data, p.buf[:p.size])
	return data
}

func (p *Packet) NoCopy() []byte {
	return p.buf[:p.size]
}

func (p *Packet) String() string {
	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("  fixed header dump:   %s\n", hex.EncodeToString(p.buf[:HeaderLength])))
	buf.WriteString(fmt.Sprintf("    Version:           %d\n", (p.buf[0]&0xc0)>>6))
	buf.WriteString(fmt.Sprintf("    Padding:           %t\n", p.Padding()))
	buf.WriteString(fmt.Sprintf("    Extension:         %t\n", p.ExtensionBit()))
	buf.WriteString(fmt.Sprintf("    Contributing SRCs: %d\n", p.CsrcCount()))
	buf.WriteString(fmt.Sprintf("    Marker:            %t\n", p.Marker()))
	buf.WriteString(fmt.Sprintf("    Payload type:      %d (0x%x)\n", p.PayloadType(), p.PayloadType()))
	buf.WriteString(fmt.Sprintf("    Sequence number:   %d (0x%x)\n", p.Sequence(), p.Sequence()))
	buf.WriteString(fmt.Sprintf("    Timestamp:         %d (0x%x)\n", p.Timestamp(), p.Timestamp()))
	buf.WriteString(fmt.Sprintf("    SSRC:              %d (0x%x)\n", p.Ssrc(), p.Ssrc()))

	if p.CsrcCount() > 0 {
		ccList := p.CsrcList()
		buf.WriteString(fmt.Sprintf("  CSRC list:\n"))
		for i, v := range ccList {
			buf.WriteString(fmt.Sprintf("      %d: %d (0x%x)\n", i, v, v))
		}
	}

	if p.ExtensionBit() {
		extLen := p.ExtensionLength()
		buf.WriteString(fmt.Sprintf("  Extentsion length: %d\n", extLen))
		offsetExt := HeaderLength + int(p.CsrcCount()<<2)
		buf.WriteString(fmt.Sprintf("    extension: %s\n", hex.EncodeToString(p.buf[offsetExt:offsetExt+extLen])))
	}
	buf.WriteString(fmt.Sprintf("  Payload length: %d\n", p.size-HeaderLength+int(p.CsrcCount()<<2)+p.ExtensionLength()))
	return buf.String()
}

type MathRandomGenerator interface {
	// IntN 返回一个随机整数，范围：[0:n)
	IntN(n int) int

	// Uint16 返回一个无符号16位整数
	Uint16() uint16

	// Uint32 返回一个无符号32位整数
	Uint32() uint32

	// Uint64 返回一个无符号64位整数
	Uint64() uint64

	// GenerateString 根据给定的字符序列返回一个随机字符串
	GenerateString(n int, runes string) string
}

type mathRandomGenerator struct {
	r  *mRand.Rand
	mu sync.Mutex
}

func CryptoUint64() (uint64, error) {
	var v uint64
	if err := binary.Read(rand.Reader, binary.LittleEndian, &v); err != nil {
		return 0, err
	}
	return v, nil
}

func NewMathRandomGenerator() MathRandomGenerator {
	seed, err := CryptoUint64()
	if err != nil {
		seed = uint64(time.Now().UnixNano())
	}
	return &mathRandomGenerator{r: mRand.New(mRand.NewSource(int64(seed)))}
}

func (g *mathRandomGenerator) IntN(n int) int {
	g.mu.Lock()
	v := g.r.Intn(n)
	g.mu.Unlock()
	return v
}

func (g *mathRandomGenerator) Uint16() uint16 {
	g.mu.Lock()
	v := g.r.Uint32()
	g.mu.Unlock()
	return uint16(v)
}

func (g *mathRandomGenerator) Uint32() uint32 {
	g.mu.Lock()
	v := g.r.Uint32()
	g.mu.Unlock()
	return v
}

func (g *mathRandomGenerator) Uint64() uint64 {
	g.mu.Lock()
	v := g.r.Uint64()
	g.mu.Unlock()
	return v
}

func (g *mathRandomGenerator) GenerateString(n int, runes string) string {
	letters := []rune(runes)
	b := make([]rune, n)
	for i := range b {
		b[i] = letters[g.IntN(len(letters))]
	}
	return string(b)
}