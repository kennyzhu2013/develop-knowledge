package dtmf

import (
	"strconv"
	"time"
)

const (
	Init uint8 = iota
	Start
	End
)

const (
	// MaxWaitTimeout 最大等待超时
	MaxWaitTimeout = 5 * 60 * 60

	// DefaultLength 默认收号长度
	DefaultLength = 4
	// DefaultTimeout 默认收号总超时或号间超时，单位s
	DefaultTimeout = 20
	// DefaultMode 默认收号模式
	DefaultMode = 1
	// DefaultTimingMode 默认计时方式
	DefaultTimingMode = 1
)

type Callback func(string)

type Detector interface {
	Check([]byte) error
	Stop() error
	SetCallback(Callback)
	SetPayloadType(uint8)
}

type DTMF struct {
	// 收号长度
	Len int
	// 第一个收号超时
	Timeout int
	// 号间超时
	IntervalTimeout int
	// 收号模式，1、一次收号；2、持续性收号
	Mode int
	// 总超时计时方式，1、放音结束后；2、全程
	TimingMode int
	// 结束标识符
	EndSign string
	// 回退标识符
	BackSign string

	// 收号结果
	data string
	// 收号状态
	state uint8
	// 静音状态
	muteState bool

	// 异步收号通知通道
	exchange chan string
	// 结束回调
	EndCallback func(string, error)
	// 一次通知回调，用于第一个收号通知
	OnceNoticeCallback func()

	// 真正检测器
	detector Detector
	// 定时器
	timer *time.Timer
}

func New(detector Detector, opt ...Option) *DTMF {
	d := new(DTMF)
	d.Init(detector, opt...)
	return d
}

func (d *DTMF) Init(detector Detector, opt ...Option) {
	for _, o := range opt {
		o.apply(d)
	}
	if d.Len <= 0 {
		d.Len = DefaultLength
	}
	if d.Timeout <= 0 {
		d.Timeout = DefaultTimeout
	}
	if d.IntervalTimeout <= 0 {
		d.IntervalTimeout = d.Timeout
	}
	if d.Mode <= 0 {
		d.Mode = DefaultMode
	}
	if d.TimingMode <= 0 {
		d.TimingMode = DefaultTimingMode
	}
	d.detector = detector
	if _, ok := d.detector.(*DetectorInBand); ok {
		d.exchange = make(chan string, 2)
		d.detector.SetCallback(d.asyncNotice)
	} else {
		d.detector.SetCallback(d.update)
	}
	d.state = Init
}

func (d *DTMF) Start() error {
	if d.state != Init {
		return ErrStatus
	}
	if d.Mode == 1 && d.TimingMode == 2 {
		d.timer = time.NewTimer(time.Duration(d.Timeout) * time.Second)
	}
	d.state = Start
	return nil
}

func (d *DTMF) Receive(packet []byte) error {
	if d.state != Start {
		return ErrStatus
	}
	var err error
	select {
	case keystroke := <-d.exchange:
		d.update(keystroke)
	default:
	}
	if d.state != Start {
		return nil
	}
	if d.Mode == 1 && d.timer == nil && d.muteState {
		d.timer = time.NewTimer(time.Duration(d.Timeout) * time.Second)
	}
	if len(packet) != 0 {
		err = d.detector.Check(packet)
	}
	if d.timer != nil {
		select {
		case <-d.timer.C:
			err = d.determine(ErrTimeout)
		default:
		}
	}
	return err
}

func (d *DTMF) Stop(cause error) error {
	if d.state == End {
		return nil
	}
	if d.EndCallback != nil {
		d.EndCallback(d.data, cause)
		d.EndCallback = nil
	}
	if d.timer != nil {
		d.timer.Stop()
		d.timer = nil
	}
	err := d.detector.Stop()
	d.state = End
	return err
}

func (d *DTMF) update(keystroke string) {
	if d.OnceNoticeCallback != nil {
		d.OnceNoticeCallback()
		d.OnceNoticeCallback = nil
	}
	if keystroke == d.EndSign {
		d.data += ":" + keystroke
		_ = d.determine(nil)
		return
	}
	if keystroke == d.BackSign {
		// 回退标识
		if len(d.data) > 0 {
			d.data = d.data[:len(d.data)-1]
		}
		d.resetTimer()
		return
	}
	d.data += keystroke
	if len(d.data) == d.Len {
		d.data += ":Len"
		_ = d.determine(nil)
		return
	}
	d.resetTimer()
}

func (d *DTMF) determine(err error) error {
	if d.Mode == 1 {
		return d.Stop(err)
	}
	if d.EndCallback != nil {
		d.EndCallback(d.data, err)
	}
	d.data = ""
	if d.timer == nil {
		return nil
	}
	if !d.timer.Stop() {
		select {
		case <-d.timer.C:
		default:
		}
	}
	d.timer.Reset(MaxWaitTimeout * time.Second)
	return nil
}

func (d *DTMF) resetTimer() {
	if d.timer == nil {
		d.timer = time.NewTimer(time.Duration(d.IntervalTimeout) * time.Second)
		return
	}
	if !d.timer.Stop() {
		select {
		case <-d.timer.C:
		default:
		}
	}
	d.timer.Reset(time.Duration(d.IntervalTimeout) * time.Second)
}

func (d *DTMF) asyncNotice(keystroke string) {
	d.exchange <- keystroke
}

func (d *DTMF) SetPayloadType(payloadType uint8) {
	d.detector.SetPayloadType(payloadType)
}

func (d *DTMF) SetMuteState() {
	d.muteState = true
}

func (d *DTMF) IsActive() bool {
	return d.state != End
}

const (
	// RtpHeaderLength Rtp头长度
	RtpHeaderLength = 12
	// PayloadTypeMask Rtp载荷类型偏移
	PayloadTypeMask = 0x7F
	// DynamicPayload 2833载荷类型下限
	DynamicPayload = 96

	// PayloadPcmu G711载荷类型
	PayloadPcmu = 0
	// PayloadPcma G711载荷类型
	PayloadPcma = 8
)

type DetectorInBand struct {
	// 音频数据缓冲区
	buf *SlideBuffer
	// 带内检测
	track *Track
}

func NewDetectorInBand(audioCode string, opt ...InBandOption) (Detector, error) {
	track, err := NewTrack(CodecType(audioCode), opt...)
	if err != nil {
		return nil, err
	}
	return &DetectorInBand{buf: NewSlideBuffer(6 * DecodeBufSize), track: track}, nil
}

func (d *DetectorInBand) Check(packet []byte) error {
	if d.track != nil {
		if len(packet) < RtpHeaderLength {
			return ErrRtpHeader
		}
		payloadType := int(packet[1] & PayloadTypeMask)
		if payloadType != PayloadPcmu && payloadType != PayloadPcma {
			return nil
		}

		_, _ = d.buf.Write(packet[RtpHeaderLength:])
		if d.buf.Len() >= DecodeBufSize {
			buf, _ := d.buf.Read(DecodeBufSize)
			if d.track.TrySend(buf) {
				_, _ = d.buf.Cut(DecodeBufSize)
			}
		}
	}
	return nil
}

func (d *DetectorInBand) Stop() error {
	if d.track != nil {
		d.track.Close()
		d.buf = nil
	}
	return nil
}

func (d *DetectorInBand) SetCallback(callback Callback) {
	if d.track != nil {
		d.track.SetCallback(callback)
	}
}

func (d *DetectorInBand) SetPayloadType(_ uint8) {}

type Detector2833 struct {
	// 为了防止丢包，同一个DTMF信号会产生多个RTP包，并且包头中Timestamp相同
	// 记录已经解析过的时间戳
	tsHistory []int64
	// 载荷类型，>= 96
	payloadType, prePayloadType uint8
	// 回调
	callback Callback
}

func NewDetector2833(payloadType uint8) (Detector, error) {
	return &Detector2833{payloadType: payloadType, tsHistory: make([]int64, 0)}, nil
}

func (d *Detector2833) Check(packet []byte) error {
	if len(packet) < RtpHeaderLength {
		return ErrRtpHeader
	}
	payloadType := packet[1] & PayloadTypeMask
	if payloadType < DynamicPayload || (payloadType != d.prePayloadType && payloadType != d.payloadType) {
		return nil
	}

	newKeystroke := true
	// 获取rtp包时间戳
	newts := GetTimestamp(packet[4:8])
	// 检测是否为重复的DTMF事件
	for _, ts := range d.tsHistory {
		if newts == ts {
			newKeystroke = false
			break
		}
	}
	if newKeystroke {
		// 新的按键
		d.tsHistory = append(d.tsHistory, newts)
		// 负载第一个字节为事件码
		d.notify(int(packet[12]))
	}
	return nil
}

func (d *Detector2833) Stop() error {
	d.tsHistory = nil
	return nil
}

func (d *Detector2833) SetCallback(callback Callback) {
	d.callback = callback
}

func (d *Detector2833) SetPayloadType(payloadType uint8) {
	if payloadType <= 0 {
		return
	}
	d.prePayloadType = d.payloadType
	d.payloadType = payloadType
}

func (d *Detector2833) notify(num int) {
	if num >= 0 && num < 10 {
		d.callback(strconv.Itoa(num))
		return
	}
	if num == 10 {
		d.callback("*")
	} else if num == 11 {
		d.callback("#")
	} else if num == 12 {
		d.callback("A")
	} else if num == 13 {
		d.callback("B")
	} else if num == 14 {
		d.callback("C")
	} else if num == 15 {
		d.callback("D")
	}
}