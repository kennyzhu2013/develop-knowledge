package rtp

import (
	"encoding/binary"
	"fmt"
)

/**
 * RTCP作用
 * 1、数据分发的质量反馈，与流量、拥塞控制相关
 * 2、携带一个持久化传输级别的标识CNAME，用于接收者跟踪每一个参与者
 * 3、方便RTP扩展更多的参与者，每个参与者可根据其他参与者的控制包来独立确定参与者的数量，该数量可用于计算控制包的发送速率
 */

/**
 * 多个RTCP包可以组合成一个复合RTCP包在底层协议中传输，其中每个RTCP包可以被单独处理。为了保证RTCP的作用，有几点约束：
 * 1、接收统计信息（SR/RR）应在带宽限制允许的情况下频繁发送，最大限度提高统计信息的分辨率，因此每个定期传输的复合RTCP包必须包含一个报告数据包
 * 2、新加入的接收者需要尽快接收发送者的CNAME，以识别发送者并关联相关媒体达到各项目的，因此每个定期传输的复合RTCP包必须包含SDES CNAME，除非复合包进行了部分加密拆分
 *
 * 所有的RTCP包必须以包含至少两个独立RTCP包的复合包形式进行发送
 * 1、当且仅当复合包被加密时，必须为每一个复合包添加一个随机32位数前缀。如果该加密被要求填充，其必须被填充到复合包最后一个独立RTCP包
 * 2、复合包的第一个RTCP包总是一个报告数据包（SR/RR），用于促进头部验证。即使没有发送或接收任何数据，甚至复合包中仅剩下BYE数据包，仍必须发送空的RR(RC=0)
 * 3、如果接收统计信息超过31个不同的源，前31个接收统计信息在被写入SR/RR数据包的同时，剩余的接收统计信息应使用额外的RR数据包进行传输，并紧跟默认报告数据包
 * 4、复合包必须包含一个携带CNAME的SDES数据包，除非复合包进行了部分加密拆分
 * 5、BYE/APP数据包和其他尚未定义的包类型可以以任意顺序跟随，但BYE数据包必须在最后一个发送，并使用给定的SSRC/CSRC。数据包类型可能出现多次
 *
 * 在一个报告周期内每个参与者应只发送一个RTCP复合包，以便正确估计每个参与者的RTCP带宽，除非复合包进行了部分加密拆分
 * 如果数据发送源的数量太多，以至于除了增加MTU这个方法外没办法将所有RR数据塞进一个复合包时，那么一次只会将部分RR数据塞进这个复合包中，其他的RR数据会在多个发送周期中被等概率的选中发送
 * 如果RTCP包的类型未知，则在实现过程中直接忽略
 */

/**
 * 数据和控制流量的带宽计算应包含底层传输和网络协议，这是由于需要被资源预留系统知道。链路级报头不包含在计算中，因为在传输过程中数据包将采用不同的链路级报头封装
 * 控制流量应是会话带宽已知的一小部分，以便传输协议传输数据的主要功能不受损害，以便包含在给定资源预留协议的带宽规范中，使每个参与者可以独立计算其带宽份额
 * 控制流量带宽是会话带宽除数据流量之外的带宽。建议添加给RTCP的会话带宽部分固定为5%；建议将RTCP的1/4带宽专用于发送数据的参与者，以便在接收多发送少的会话中新加入的参与者更快地接收发送方的CNAME。当发送方比例超过参与者的1/4时，发送方将获得其在整个RTCP带宽中的比例
 * RTCP复合包的传输间隔应具有下限，避免在参与者数量较少且流量未根据大数定律进行平滑处理时，数据包突发超过允许的带宽；还可以防止报告间隔在暂时性中断（如网络分区）期间变得太小，以便在分区愈合时延迟适应
 * 应用程序启动时，应在发送第一个RTCP复合包之前施加延迟，以便有时间从其他参与者接收RTCP数据包，使报告间隔更快地收敛到正确的值。该延迟可以设置为最小间隔（5秒）的一半
 */

const (
	// RtcpSR 发送者报告
	RtcpSR uint8 = 200
	// RtcpRR 接收者报告
	RtcpRR uint8 = 201
	// RtcpSDES RTP会话描述
	RtcpSDES uint8 = 202
	// RtcpBYE RTP会话结束
	RtcpBYE uint8 = 203
	// RtcpAPP 应用自定义
	RtcpAPP uint8 = 204
	// RtcpXR 扩展报告
	RtcpXR uint8 = 207
)

const (
	// SDESEnd end of SDES list RFC 3550, 6.5
	SDESEnd uint8 = iota
	// SDESCNAME canonical name RFC 3550, 6.5.1
	SDESCNAME
	// SDESName user name RFC 3550, 6.5.2
	SDESName
	// SDESEmail user's electronic mail address RFC 3550, 6.5.3
	SDESEmail
	// SDESPhone user's phone number RFC 3550, 6.5.4
	SDESPhone
	// SDESLocation // geographic user location RFC 3550, 6.5.5
	SDESLocation
	// SDESTool name of application or tool RFC 3550, 6.5.6
	SDESTool
	// SDESNote notice about the source RFC 3550, 6.5.7
	SDESNote
	// SDESPrivate private extensions RFC 3550, 6.5.8  (not implemented)
	SDESPrivate
)

const (
	rtcpHeaderLength        = 4
	reportBlockLength       = 24
	maxSsrcOrCsrc           = 32
	minSenderReportLength   = 28
	minReceiverReportLength = 8
	minAppLength            = 12
)

const (
	receptionReportLength = 24
	fractionLostOffset    = 4
	totalLostOffset       = 5
	lastSeqOffset         = 8
	jitterOffset          = 12
	lastSROffset          = 16
	delayOffset           = 20
)

const (
	headerSRCOffset = 0
	headerPTOffset  = 1
	headerLenOffset = 2

	senderSsrcOffset                  = 4
	senderInfoNtpMSWOffset            = 8
	senderInfoNtpLSWOffset            = 12
	senderInfoRtpTSOffset             = 16
	senderInfoPacketCntOffset         = 20
	senderInfoOctetCntOffset          = 24
	senderReportBlockSsrcOffset       = 28
	senderReportBlockFracLostOffset   = 32
	senderReportBlockLostCntOffset    = 33
	senderReportBlockHighestSeqOffset = 36
	senderReportBlockJitterOffset     = 40
	senderReportBlockLSROffset        = 44
	senderReportBlockDLSROffset       = 48

	receiverReportBlockSsrcOffset       = 8
	receiverReportBlockFracLostOffset   = 12
	receiverReportBlockLostCntOffset    = 13
	receiverReportBlockHighestSeqOffset = 16
	receiverReportBlockJitterOffset     = 20
	receiverReportBlockLSROffset        = 24
	receiverReportBlockDLSROffset       = 28

	sdesChunkSsrcOffset   = 0
	sdesChunkTypeOffset   = 4
	sdesChunkLengthOffset = 5
	sdesChunkDataOffset   = 6

	appSsrcOffset = 4
	appNameOffset = 8
	appDataOffset = 12

	pBit   = 0x20
	srcBit = 0x1F
)

type RtcpPacket struct {
	buf  []byte
	pad  int
	size int
}

// Header
// 头部信息，8字节长度

func (p *RtcpPacket) Padding() bool {
	return p.buf[headerSRCOffset]&pBit == pBit
}

// SetPadding 当设置填充位，RTCP包将在尾部包含一些额外的填充字节，最后一个字节指示了多少个填充字节，包括其本身（4的倍数），填充字节的数量也包含在长度字段length中
// 在RTCP复合包中，字节填充仅被要求填充到最后一个RTCP包中，并且填充位设置也仅能是最后一个
func (p *RtcpPacket) SetPadding(padding bool, pad int) {
	if padding {
		p.buf[headerSRCOffset] |= pBit
		if pad == 0 {
			p.pad = 4
		} else {
			p.pad = pad
		}
	} else {
		p.buf[headerSRCOffset] &^= pBit
		p.pad = 0
	}
}

// SourceCount 指示了当前RTCP包携带的统计报告块或SSRC/CSRC数量，可以为0
func (p *RtcpPacket) SourceCount() uint8 {
	return p.buf[headerSRCOffset] & srcBit
}

func (p *RtcpPacket) SetSourceCount(rc uint8) {
	p.buf[headerSRCOffset] &^= srcBit
	p.buf[headerSRCOffset] |= rc & srcBit
}

func (p *RtcpPacket) PayloadType() uint8 {
	return p.buf[headerPTOffset]
}

func (p *RtcpPacket) SetPayloadType(pt uint8) {
	p.buf[headerPTOffset] = pt
}

// Length RTCP包长度-1，包含了头部和填充字节，单位为4字节
func (p *RtcpPacket) Length() uint16 {
	return (binary.BigEndian.Uint16(p.buf[headerLenOffset:]) + 1) << 2
}

func (p *RtcpPacket) SetLength(length uint16) {
	binary.BigEndian.PutUint16(p.buf[headerLenOffset:], (length>>2)-1)
}

/**
 * 		   			0 			    1 			    2 			    3
 * 			0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * header 	|V=2|P|    RC  	| 	PT=SR=200 	| 			  length 			|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 						  SSRC of sender 						|
 * 			+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * sender 	| 			   NTP timestamp, most significant word 			|
 *  info 	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 			  NTP timestamp, least significant word 			|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 						  RTP timestamp 						|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 					  sender’s packet count 					|
 *			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 					   sender’s octet count 					|
 * 			+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * report 	| 				  SSRC_1 (SSRC of first source) 				|
 * block 	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   1 		| fraction lost | 		cumulative number of packets lost 		|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 			extended highest sequence number received 			|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 					   interarrival jitter 						|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 						  last SR (LSR) 						|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 			| 				    delay since last SR (DLSR) 					|
 *			+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * report 	| 				  SSRC_2 (SSRC of second source) 				|
 * block 	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   2 		: 								... 							:
 * 			+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * 			|				   profile-specific extensions 					|
 * 			+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

type SenderReport struct {
	RtcpPacket
}

type ReceptionReport struct {
	SSRC      uint32
	FracLost  uint8
	TotalLost uint32
	LastSeq   uint32
	Jitter    uint32
	LastSR    uint32
	Delay     uint32
}

func NewSenderReport() *SenderReport {
	buf := make([]byte, defaultBufSize)
	return &SenderReport{RtcpPacket: RtcpPacket{buf: buf, size: minSenderReportLength}}
}

// SenderInfo
// 发送者信息，20字节长度，在每个SR中都存在，概述了发送者的数据传输信息

func (s *SenderReport) Ssrc() uint32 {
	return binary.BigEndian.Uint32(s.buf[senderSsrcOffset:])
}

func (s *SenderReport) SetSsrc(ssrc uint32) {
	binary.BigEndian.PutUint32(s.buf[senderSsrcOffset:], ssrc)
}

// NtpMSW 没有挂钟或经过时间概念的发送者可以将NTP时间戳设置为零
func (s *SenderReport) NtpMSW() uint32 {
	return binary.BigEndian.Uint32(s.buf[senderInfoNtpMSWOffset:])
}

func (s *SenderReport) SetNtpMSW(msw uint32) {
	binary.BigEndian.PutUint32(s.buf[senderInfoNtpMSWOffset:], msw)
}

// NtpLSW 没有挂钟或经过时间概念的发送者可以将NTP时间戳设置为零
func (s *SenderReport) NtpLSW() uint32 {
	return binary.BigEndian.Uint32(s.buf[senderInfoNtpLSWOffset:])
}

func (s *SenderReport) SetNtpLSW(lsw uint32) {
	binary.BigEndian.PutUint32(s.buf[senderInfoNtpLSWOffset:], lsw)
}

func (s *SenderReport) NTPTime() uint64 {
	return binary.BigEndian.Uint64(s.buf[senderInfoNtpMSWOffset:])
}

func (s *SenderReport) Timestamp() uint32 {
	return binary.BigEndian.Uint32(s.buf[senderInfoRtpTSOffset:])
}

func (s *SenderReport) SetTimestamp(timestamp uint32) {
	binary.BigEndian.PutUint32(s.buf[senderInfoRtpTSOffset:], timestamp)
}

// PacketCount RTP包的发送数量，如果SSRC发生改变，该数量应该被重置
func (s *SenderReport) PacketCount() uint32 {
	return binary.BigEndian.Uint32(s.buf[senderInfoPacketCntOffset:])
}

func (s *SenderReport) SetPacketCount(pCnt uint32) {
	binary.BigEndian.PutUint32(s.buf[senderInfoPacketCntOffset:], pCnt)
}

// OctetCount RTP包载荷发送的总字节数，不包含头部和填充，如果SSRC发生改变，该字节数应该被重置
// 该值可用于估计载荷的平均传输速率
func (s *SenderReport) OctetCount() uint32 {
	return binary.BigEndian.Uint32(s.buf[senderInfoOctetCntOffset:])
}

func (s *SenderReport) SetOctetCount(oCnt uint32) {
	binary.BigEndian.PutUint32(s.buf[senderInfoOctetCntOffset:], oCnt)
}

// Reception report blocks
// 统计报告块信息，包含零个或多个，具体数量取决于从上一次发送报告以来接收到的其他源的报告信息
// 当发生源冲突引起的SSRC改变，接收者不应该携带该SSRC的统计信息

func (s *SenderReport) AddReceptionReport(report ReceptionReport) {
	binary.BigEndian.PutUint32(s.buf[s.size:], report.SSRC)
	s.buf[s.size+fractionLostOffset] = report.FracLost
	s.buf[s.size+totalLostOffset] = byte(report.TotalLost >> 16)
	s.buf[s.size+totalLostOffset+1] = byte(report.TotalLost >> 8)
	s.buf[s.size+totalLostOffset+2] = byte(report.TotalLost)
	binary.BigEndian.PutUint32(s.buf[s.size+lastSeqOffset:], report.LastSeq)
	binary.BigEndian.PutUint32(s.buf[s.size+jitterOffset:], report.Jitter)
	binary.BigEndian.PutUint32(s.buf[s.size+lastSROffset:], report.LastSR)
	binary.BigEndian.PutUint32(s.buf[s.size+delayOffset:], report.Delay)
	s.SetSourceCount(s.SourceCount() + 1)
	s.size += receptionReportLength
}

func (s *SenderReport) SourceSsrc(blockIndex uint8) uint32 {
	if blockIndex < s.SourceCount() {
		return binary.BigEndian.Uint32(s.buf[senderReportBlockSsrcOffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (s *SenderReport) SetSourceSsrc(blockIndex uint8, ssrc uint32) {
	if blockIndex < s.SourceCount() {
		binary.BigEndian.PutUint32(s.buf[senderReportBlockSsrcOffset+blockIndex*reportBlockLength:], ssrc)
	}
}

// FractionLost 丢包率，表示自上一次SR或RR包发送以来，来源该SSRC源的RTP包丢失的部分，记作(期望收包数-实际收包数)/期望收包数*256（当前周期）
// 丢包的计算是根据RTP包中的Seq，由于Seq仅被分配16bit，即最大值为65535，容易用尽，需进行循环计数转化为uint32表示
func (s *SenderReport) FractionLost(blockIndex uint8) uint8 {
	if blockIndex < s.SourceCount() {
		return s.buf[senderReportBlockFracLostOffset+blockIndex*reportBlockLength]
	}
	return 0
}

func (s *SenderReport) SetFractionLost(blockIndex uint8, fractionLost uint8) {
	if blockIndex < s.SourceCount() {
		s.buf[senderReportBlockFracLostOffset+blockIndex*reportBlockLength] = fractionLost
	}
}

// LostPacketCnt 自报告统计以来该SSRC源丢失的RTP包总数，记作 期望收包数-实际收包数
// 其中，期望收包数，实际收包数：
func (s *SenderReport) LostPacketCnt(blockIndex uint8) int32 {
	if blockIndex < s.SourceCount() {
		blockIndex = senderReportBlockLostCntOffset + blockIndex*reportBlockLength
		return (int32(s.buf[blockIndex]) << 16) | (int32(s.buf[blockIndex+1]) << 8) | int32(s.buf[blockIndex+2])
	}
	return 0
}

func (s *SenderReport) SetLostPacketCnt(blockIndex uint8, cnt int32) {
	if blockIndex < s.SourceCount() {
		blockIndex = senderReportBlockLostCntOffset + blockIndex*reportBlockLength
		s.buf[blockIndex] = byte(cnt >> 16)
		s.buf[blockIndex+1] = byte(cnt >> 8)
		s.buf[blockIndex+2] = byte(cnt)
	}
}

func (s *SenderReport) HighestSeq(blockIndex uint8) uint32 {
	if blockIndex < s.SourceCount() {
		return binary.BigEndian.Uint32(s.buf[senderReportBlockHighestSeqOffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (s *SenderReport) SetHighestSeq(blockIndex uint8, seq uint32) {
	if blockIndex < s.SourceCount() {
		binary.BigEndian.PutUint32(s.buf[senderReportBlockHighestSeqOffset+blockIndex*reportBlockLength:], seq)
	}
}

func (s *SenderReport) InterarrivalJitter(blockIndex uint8) uint32 {
	if blockIndex < s.SourceCount() {
		return binary.BigEndian.Uint32(s.buf[senderReportBlockJitterOffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (s *SenderReport) SetInterarrivalJitter(blockIndex uint8, jitter uint32) {
	if blockIndex < s.SourceCount() {
		binary.BigEndian.PutUint32(s.buf[senderReportBlockJitterOffset+blockIndex*reportBlockLength:], jitter)
	}
}

func (s *SenderReport) LSR(blockIndex uint8) uint32 {
	if blockIndex < s.SourceCount() {
		return binary.BigEndian.Uint32(s.buf[senderReportBlockLSROffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (s *SenderReport) SetLSR(blockIndex uint8, lSR uint32) {
	if blockIndex < s.SourceCount() {
		binary.BigEndian.PutUint32(s.buf[senderReportBlockLSROffset+blockIndex*reportBlockLength:], lSR)
	}
}

func (s *SenderReport) DLSR(blockIndex uint8) uint32 {
	if blockIndex < s.SourceCount() {
		return binary.BigEndian.Uint32(s.buf[senderReportBlockDLSROffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (s *SenderReport) SetDLSR(blockIndex uint8, dLSR uint32) {
	if blockIndex < s.SourceCount() {
		binary.BigEndian.PutUint32(s.buf[senderReportBlockDLSROffset+blockIndex*reportBlockLength:], dLSR)
	}
}

func (s *SenderReport) Unmarshal(raw []byte) error {
	if len(raw) < minSenderReportLength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpSR {
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
		return errInvalidLength
	}
	size := minSenderReportLength
	for i, n := uint8(0), raw[headerSRCOffset]&srcBit; i < n; i++ {
		if size+reportBlockLength > len(raw) {
			return errPacketTooShort
		}
		size += reportBlockLength
	}
	s.size = copy(s.buf, raw[:len(raw)-pad])
	s.pad = pad
	return nil
}

func (s *SenderReport) String() string {
	out := fmt.Sprintf("SenderReport from %x\n", s.Ssrc())
	out += fmt.Sprintf("\tNTPTime:\t%d\n", s.NTPTime())
	out += fmt.Sprintf("\tRTPTIme:\t%d\n", s.Timestamp())
	out += fmt.Sprintf("\tPacketCount:\t%d\n", s.PacketCount())
	out += fmt.Sprintf("\tOctetCount:\t%d\n", s.OctetCount())

	out += "\tSSRC    \tLost\tLastSequence\n"
	for i, n := uint8(0), s.SourceCount(); i < n; i++ {
		out += fmt.Sprintf("\t%x\t%d/%d\t%d\n", s.SourceSsrc(i), s.FractionLost(i), s.LostPacketCnt(i), s.HighestSeq(i))
	}
	return out
}

/*
 *         			0               1               2               3
 *         	0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *        	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * header 	|V=2|P|    RC   |   PT=RR=201   |             length            |
 *        	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        	|                     SSRC of packet sender                     |
 *        	+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * report 	|                 SSRC_1 (SSRC of first source)                 |
 * block  	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   1    	| fraction lost |       cumulative number of packets lost       |
 *        	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        	|           extended highest sequence number received           |
 *        	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        	|                      interarrival jitter                      |
 *        	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        	|                         last SR (LSR)                         |
 *        	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        	|                   delay since last SR (DLSR)                  |
 *        	+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * report 	|                 SSRC_2 (SSRC of second source)                |
 * block  	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   2    	:                               ...                             :
 *       	+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        	|                  profile-specific extensions                  |
 *        	+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

type ReceiverReport struct {
	RtcpPacket
}

func NewReceiverReport() *ReceiverReport {
	buf := make([]byte, defaultBufSize)
	return &ReceiverReport{RtcpPacket: RtcpPacket{buf: buf, size: minReceiverReportLength}}
}

func (r *ReceiverReport) Ssrc() uint32 {
	return binary.BigEndian.Uint32(r.buf[senderSsrcOffset:])
}

func (r *ReceiverReport) SetSsrc(ssrc uint32) {
	binary.BigEndian.PutUint32(r.buf[senderSsrcOffset:], ssrc)
}

// Reception report blocks
// 统计报告块信息，包含零个或多个，具体数量取决于从上一次发送报告以来接收到的其他源的报告信息
// 当发生源冲突引起的SSRC改变，接收者不应该携带该SSRC的统计信息

func (r *ReceiverReport) AddReceptionReport(report ReceptionReport) {
	binary.BigEndian.PutUint32(r.buf[r.size:], report.SSRC)
	r.buf[r.size+fractionLostOffset] = report.FracLost
	r.buf[r.size+totalLostOffset] = byte(report.TotalLost >> 16)
	r.buf[r.size+totalLostOffset+1] = byte(report.TotalLost >> 8)
	r.buf[r.size+totalLostOffset+2] = byte(report.TotalLost)
	binary.BigEndian.PutUint32(r.buf[r.size+lastSeqOffset:], report.LastSeq)
	binary.BigEndian.PutUint32(r.buf[r.size+jitterOffset:], report.Jitter)
	binary.BigEndian.PutUint32(r.buf[r.size+lastSROffset:], report.LastSR)
	binary.BigEndian.PutUint32(r.buf[r.size+delayOffset:], report.Delay)
	r.SetSourceCount(r.SourceCount() + 1)
	r.size += receptionReportLength
}

func (r *ReceiverReport) SourceSsrc(blockIndex uint8) uint32 {
	if blockIndex < r.SourceCount() {
		return binary.BigEndian.Uint32(r.buf[receiverReportBlockSsrcOffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (r *ReceiverReport) SetSourceSsrc(blockIndex uint8, ssrc uint32) {
	if blockIndex < r.SourceCount() {
		binary.BigEndian.PutUint32(r.buf[receiverReportBlockSsrcOffset+blockIndex*reportBlockLength:], ssrc)
	}
}

// FractionLost 丢包率，表示自上一次SR或RR包发送以来，来源该SSRC源的RTP包丢失的部分，记作(期望收包数-实际收包数)/期望收包数*256（当前周期）
// 丢包的计算是根据RTP包中的Seq，由于Seq仅被分配16bit，即最大值为65535，容易用尽，需进行循环计数转化为uint32表示
func (r *ReceiverReport) FractionLost(blockIndex uint8) uint8 {
	if blockIndex < r.SourceCount() {
		return r.buf[receiverReportBlockFracLostOffset+blockIndex*reportBlockLength]
	}
	return 0
}

func (r *ReceiverReport) SetFractionLost(blockIndex uint8, fractionLost uint8) {
	if blockIndex < r.SourceCount() {
		r.buf[receiverReportBlockFracLostOffset+blockIndex*reportBlockLength] = fractionLost
	}
}

// LostPacketCnt 自报告统计以来该SSRC源丢失的RTP包总数，记作 期望收包数-实际收包数
// 其中，期望收包数，实际收包数：
func (r *ReceiverReport) LostPacketCnt(blockIndex uint8) int32 {
	if blockIndex < r.SourceCount() {
		blockIndex = receiverReportBlockLostCntOffset + blockIndex*reportBlockLength
		return (int32(r.buf[blockIndex]) << 16) | (int32(r.buf[blockIndex+1]) << 8) | int32(r.buf[blockIndex+2])
	}
	return 0
}

func (r *ReceiverReport) SetLostPacketCnt(blockIndex uint8, cnt int32) {
	if blockIndex < r.SourceCount() {
		blockIndex = receiverReportBlockLostCntOffset + blockIndex*reportBlockLength
		r.buf[blockIndex] = byte(cnt >> 16)
		r.buf[blockIndex+1] = byte(cnt >> 8)
		r.buf[blockIndex+2] = byte(cnt)
	}
}

// HighestSeq 低16bit代表从RTP包接收到的最高序列号，高16bit代表循环计数
func (r *ReceiverReport) HighestSeq(blockIndex uint8) uint32 {
	if blockIndex < r.SourceCount() {
		return binary.BigEndian.Uint32(r.buf[receiverReportBlockHighestSeqOffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (r *ReceiverReport) SetHighestSeq(blockIndex uint8, seq uint32) {
	if blockIndex < r.SourceCount() {
		binary.BigEndian.PutUint32(r.buf[receiverReportBlockHighestSeqOffset+blockIndex*reportBlockLength:], seq)
	}
}

func (r *ReceiverReport) InterarrivalJitter(blockIndex uint8) uint32 {
	if blockIndex < r.SourceCount() {
		return binary.BigEndian.Uint32(r.buf[receiverReportBlockJitterOffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (r *ReceiverReport) SetInterarrivalJitter(blockIndex uint8, jitter uint32) {
	if blockIndex < r.SourceCount() {
		binary.BigEndian.PutUint32(r.buf[receiverReportBlockJitterOffset+blockIndex*reportBlockLength:], jitter)
	}
}

// LSR 最近接收的SR中，64位NTP时间戳中间的32bit
// 如果还未接收到任何的SR，则设置为0
func (r *ReceiverReport) LSR(blockIndex uint8) uint32 {
	if blockIndex < r.SourceCount() {
		return binary.BigEndian.Uint32(r.buf[receiverReportBlockLSROffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (r *ReceiverReport) SetLSR(blockIndex uint8, lSR uint32) {
	if blockIndex < r.SourceCount() {
		binary.BigEndian.PutUint32(r.buf[receiverReportBlockLSROffset+blockIndex*reportBlockLength:], lSR)
	}
}

// DLSR 最近接收的SR到发送该报告块之间的延迟，以1/65536秒为单位表示
// 如果还未接收到任何的SR，则设置为0
func (r *ReceiverReport) DLSR(blockIndex uint8) uint32 {
	if blockIndex < r.SourceCount() {
		return binary.BigEndian.Uint32(r.buf[receiverReportBlockDLSROffset+blockIndex*reportBlockLength:])
	}
	return 0
}

func (r *ReceiverReport) SetDLSR(blockIndex uint8, dLSR uint32) {
	if blockIndex < r.SourceCount() {
		binary.BigEndian.PutUint32(r.buf[receiverReportBlockDLSROffset+blockIndex*reportBlockLength:], dLSR)
	}
}

func (r *ReceiverReport) Unmarshal(raw []byte) error {
	if len(raw) < minReceiverReportLength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpRR {
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
		return errInvalidLength
	}
	size := minReceiverReportLength
	for i, n := uint8(0), raw[headerSRCOffset]&srcBit; i < n; i++ {
		if size+reportBlockLength > len(raw) {
			return errPacketTooShort
		}
		size += reportBlockLength
	}
	r.size = copy(r.buf, raw[:len(raw)-pad])
	r.pad = pad
	return nil
}

func (r *ReceiverReport) String() string {
	out := fmt.Sprintf("ReceiverReport from %x\n", r.Ssrc())
	out += "\tSSRC    \tLost\tLastSequence\n"
	for i, n := uint8(0), r.SourceCount(); i < n; i++ {
		out += fmt.Sprintf("\t%x\t%d/%d\t%d\n", r.SourceSsrc(i), r.FractionLost(i), r.LostPacketCnt(i), r.HighestSeq(i))
	}
	return out
}

/*
 *         		  0               1               2               3
 *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * header |V=2|P|    SC   |  PT=SDES=202  |             length            |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * chunk  |                          SSRC/CSRC_1                          |
 *   1    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |    CNAME=1    |     length    |     user and domain name    ...
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                              ...                              |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * chunk  |                          SSRC/CSRC_2                          |
 *   2    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                           SDES items                          |
 *        |                              ...                              |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 */

type SourceDescription struct {
	RtcpPacket
}

func NewSourceDescription() *SourceDescription {
	buf := make([]byte, defaultBufSize)
	return &SourceDescription{RtcpPacket: RtcpPacket{buf: buf, size: rtcpHeaderLength}}
}

func (s *SourceDescription) Chunk(cumulativeChunkLength uint) *SourceDescriptionChunk {
	if s.SourceCount() > 0 {
		for i := rtcpHeaderLength + cumulativeChunkLength; i < uint(s.size); {
			if s.buf[i] == SDESEnd {
				return &SourceDescriptionChunk{buf: s.buf[rtcpHeaderLength+cumulativeChunkLength : i], size: int(i - rtcpHeaderLength - cumulativeChunkLength)}
			}
			i += uint(s.buf[i+sdesChunkLengthOffset]) + sdesChunkLengthOffset + 1
		}
	}
	return nil
}

func (s *SourceDescription) AddChunk(chunk *SourceDescriptionChunk) {
	if chunk == nil || chunk.size < sdesChunkTypeOffset {
		return
	}
	n := copy(s.buf[s.size:], chunk.Marshal())
	if n != chunk.Len() {
		return
	}
	s.SetSourceCount(s.SourceCount() + 1)
	s.size += chunk.Len()
}

func (s *SourceDescription) Unmarshal(raw []byte) error {
	if len(raw) < rtcpHeaderLength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpSDES {
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
		return errInvalidLength
	}
	cLen := rtcpHeaderLength
	for i, n := uint8(0), raw[headerSRCOffset]&srcBit; i < n; i++ {
		for cLen < len(raw) {
			if raw[cLen] == SDESEnd {
				break
			}
			chunkLenOffset := cLen + sdesChunkLengthOffset
			if chunkLenOffset >= len(raw) {
				return errPacketTooShort
			}
			cLen += int(raw[chunkLenOffset] + 6)
		}
		cLen += GetPadding(cLen)
		if cLen > len(raw) {
			return errPacketTooShort
		}
	}
	if cLen+pad != len(raw) {
		return errInvalidHeader
	}
	s.size = copy(s.buf, raw[:len(raw)-pad])
	s.pad = pad
	return nil
}

func (s *SourceDescription) String() string {
	out := "Source Description:\n"
	for i, n, cLen := uint8(0), s.SourceCount(), 0; i < n; i++ {
		chunk := s.Chunk(uint(i))
		out += fmt.Sprintf("\t%v\n", chunk)
		cLen += chunk.Len()
	}
	return out
}

// chunk
// 由一个SSRC/CSRC源标识，紧跟零个或多个SDES item组成，多个SDES item是连续存储的，不会单独填充到4byte对齐
// SDES item列表最终都会以一个空字节结束，代表SDESType=0，由于每一个chunk都从4byte边界开始，不足4byte边界时需零字节填充

type SourceDescriptionChunk struct {
	buf  []byte
	size int
}

func (c *SourceDescriptionChunk) Ssrc() uint32 {
	return binary.BigEndian.Uint32(c.buf[sdesChunkSsrcOffset:])
}

func (c *SourceDescriptionChunk) SetSsrc(ssrc uint32) {
	binary.BigEndian.PutUint32(c.buf[sdesChunkSsrcOffset:], ssrc)
}

func (c *SourceDescriptionChunk) ItemType(cumulativeItemLength uint) uint8 {
	return c.buf[sdesChunkTypeOffset+cumulativeItemLength]
}

// ItemLength 描述文本长度，不包含两字节头部
func (c *SourceDescriptionChunk) ItemLength(cumulativeItemLength uint) uint8 {
	return c.buf[sdesChunkLengthOffset+cumulativeItemLength]
}

// ItemData UTF-8，长度不超过255字节
func (c *SourceDescriptionChunk) ItemData(cumulativeItemLength uint) string {
	return string(c.buf[sdesChunkDataOffset+cumulativeItemLength : c.ItemLength(cumulativeItemLength)])
}

func (c *SourceDescriptionChunk) AddItem(typ uint8, text string) {
	if typ < SDESCNAME || typ > SDESPrivate {
		return
	}
	if len(text) == 0 {
		return
	}
	if c.size+len(text) > len(c.buf) {
		return
	}
	c.buf[c.size] = typ
	c.buf[c.size+1] = uint8(len(text))
	c.size += copy(c.buf[c.size+2:], text) + 2
}

func (c *SourceDescriptionChunk) Marshal() []byte {
	c.buf[c.size] = SDESEnd
	pad := GetPadding(c.size + 1)
	for i := 0; i < pad; i++ {
		c.buf[c.size+1+i] = 0
	}
	return c.buf[:c.size+pad+1]
}

func (c *SourceDescriptionChunk) String() string {
	return fmt.Sprintf("%x: %v", c.Ssrc(), c.ItemType(0))
}

func (c *SourceDescriptionChunk) Len() int {
	return c.size + GetPadding(c.size)
}

/*
 *        		 0               1               2               3
 *       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *       |V=2|P|    SC   |   PT=BYE=203  |             length            |
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *       |                           SSRC/CSRC                           |
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *       :                              ...                              :
 *       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * (opt) |     length    |               reason for leaving            ...
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

type Bye struct {
	RtcpPacket
}

func NewBye() *Bye {
	buf := make([]byte, defaultBufSize)
	return &Bye{RtcpPacket: RtcpPacket{buf: buf, size: rtcpHeaderLength}}
}

func (b *Bye) SourceList() []uint32 {
	srcList := make([]uint32, b.SourceCount())
	for i := range srcList {
		srcList[i] = binary.BigEndian.Uint32(b.buf[rtcpHeaderLength+(i<<2):])
	}
	return srcList
}

func (b *Bye) SetSourceList(srcList []uint32) {
	if len(srcList) > maxSsrcOrCsrc {
		return
	}
	offsetOld := int(b.SourceCount()<<2) + rtcpHeaderLength
	offsetNew := (len(srcList) << 2) + rtcpHeaderLength
	// 计算新数据包大小
	newSize := offsetNew + (b.size - offsetOld)
	if newSize > cap(b.buf) {
		return
	}

	// 重新定位载荷
	copy(b.buf[offsetNew:], b.buf[offsetOld:b.size])
	// 添加覆盖原CSRC
	for i, src := range srcList {
		binary.BigEndian.PutUint32(b.buf[rtcpHeaderLength+(i<<2):], src)
	}

	// 设置SC
	b.buf[headerSRCOffset] &^= srcBit
	b.buf[headerSRCOffset] |= byte(len(srcList) & srcBit)
	// 设置新数据包大小
	b.size = newSize
}

func (b *Bye) ReasonLength() uint8 {
	offsetReason := int(b.SourceCount()<<2) + rtcpHeaderLength
	if b.size == offsetReason {
		return 0
	}
	return b.buf[offsetReason]
}

// Reason 如果长度不满足4字节边界，需填充零字节
func (b *Bye) Reason() string {
	offsetReason := int(b.SourceCount()<<2) + rtcpHeaderLength
	if b.size == offsetReason {
		return ""
	}
	return string(b.buf[offsetReason+1 : int(b.buf[offsetReason])+offsetReason+1])
}

func (b *Bye) SetReason(reason string) {
	if len(reason) == 0 {
		return
	}
	offsetReason := int(b.SourceCount()<<2) + rtcpHeaderLength
	b.buf[offsetReason] = byte(len(reason))
	b.size = offsetReason + copy(b.buf[offsetReason+1:], reason) + 1
}

func (b *Bye) Unmarshal(raw []byte) error {
	if len(raw) < rtcpHeaderLength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpBYE {
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
		return errInvalidLength
	}
	size := rtcpHeaderLength
	for i, n := uint8(0), raw[headerSRCOffset]&srcBit; i < n; i++ {
		if size+4 > len(raw) {
			return errPacketTooShort
		}
		size += 4
	}
	b.size = copy(b.buf, raw[:len(raw)-pad])
	b.pad = pad
	return nil
}

func (b *Bye) String() string {
	out := "Goodbye\n"
	for i, s := range b.SourceList() {
		out += fmt.Sprintf("\tSource %d: %x\n", i, s)
	}
	out += fmt.Sprintf("\tReason: %s\n", b.Reason())
	return out
}

/*
 *        		 0               1               2               3
 *       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *       |V=2|P| subtype |   PT=APP=204  |             length            |
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *       |                           SSRC/CSRC                           |
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *       |                          name (ASCII)                         |
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 		 |     				  application-dependent data               ...
 *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

type App struct {
	RtcpPacket
}

func NewApp() *App {
	buf := make([]byte, defaultBufSize)
	return &App{RtcpPacket: RtcpPacket{buf: buf, size: minAppLength}}
}

func (a *App) Ssrc() uint32 {
	return binary.BigEndian.Uint32(a.buf[appSsrcOffset:])
}

func (a *App) SetSsrc(ssrc uint32) {
	binary.BigEndian.PutUint32(a.buf[appSsrcOffset:], ssrc)
}

func (a *App) Name() string {
	return string(a.buf[appNameOffset:appDataOffset])
}

func (a *App) SetName(name string) {
	if len(name) <= 4 {
		copy(a.buf[appNameOffset:], []byte{0, 0, 0, 0})
		for i, j := len(name)-1, 3; i >= 0; i-- {
			a.buf[appNameOffset+j] = name[i]
			j--
		}
	}
}

// Data 如果长度不满足4字节边界，需填充零字节
func (a *App) Data() string {
	if a.size > appDataOffset {
		return string(a.buf[appDataOffset:a.size])
	}
	return ""
}

func (a *App) SetData(data string) {
	if len(data) != 0 {
		a.size = appDataOffset + copy(a.buf[appDataOffset:], data)
	}
}

func (a *App) Marshal() []byte {
	pad := GetPadding(a.size)
	if a.buf[headerSRCOffset]&pBit == pBit {
		a.buf[a.size+pad+a.pad-1] = byte(a.pad)
		pad += a.pad - 1
	}
	for i := 0; i < pad; i++ {
		a.buf[a.size+i] = 0
	}
	return a.buf[:a.size+pad+1]
}

func (a *App) Unmarshal(raw []byte) error {
	if len(raw) < minAppLength {
		return errPacketTooShort
	}
	if raw[headerPTOffset] != RtcpAPP {
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
		return errInvalidLength
	}
	a.size = copy(a.buf, raw[:len(raw)-pad])
	a.pad = pad
	return nil
}

func (a *App) String() string {
	out := "Application\n"
	out += fmt.Sprintf("\t%x: %s\n", a.Ssrc(), a.Name())
	return out
}