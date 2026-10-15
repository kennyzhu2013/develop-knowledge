package dtmf

import (
	"cmic.chinamobile.com/ziyan/codecx"
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"errors"
	"sync"
	"sync/atomic"
)

type CodecType string

const (
	CodecAmrNB      CodecType = "audio/amr"
	CodecAmrNBOctet CodecType = "audio/amr-octet"
	CodecAmrWB      CodecType = "audio/amr-wb"
	CodecAmrWBOctet CodecType = "audio/amr-wb-octet"
	CodecPcma       CodecType = "audio/pcma"
	CodecPcmu       CodecType = "audio/pcmu"
)

// DecodeBufSize
// 音频解码缓冲区大小，必须为160的整数倍
const DecodeBufSize = 160

type Track struct {
	sema int32
	opts InBandOptions

	// 媒体数据输入队列
	queue chan []byte

	// 上次检测剩余采样
	pos       int
	remainBuf []byte

	aDecoder codecx.AVCodec
	inBand   *InBand

	// 回调
	callback func(message string)
	// 退出信号通道
	exit chan struct{}
	// 关闭句柄
	sync.Once
}

func NewTrack(codecType CodecType, opts ...InBandOption) (*Track, error) {
	var err error
	var aDecoder codecx.AVCodec
	if codecType == CodecPcma {
		aDecoder, err = codecx.NewDecoder(codecx.MimeForAVCodecParameters(codecx.MimeTypePCMA), codecx.NewAvCodecParameters(codecx.ChannelLayout(codecx.Mono()),
			codecx.SampleRate(codecx.SampleRate8k), codecx.SampleFormat(codecx.AVSampleFormatS16)))
	} else if codecType == CodecPcmu {
		aDecoder, err = codecx.NewDecoder(codecx.MimeForAVCodecParameters(codecx.MimeTypePCMU), codecx.NewAvCodecParameters(codecx.ChannelLayout(codecx.Mono()),
			codecx.SampleRate(codecx.SampleRate8k), codecx.SampleFormat(codecx.AVSampleFormatS16)))
	} else if codecType == CodecAmrNB || codecType == CodecAmrNBOctet {
		aDecoder, err = codecx.NewDecoder(codecx.MimeForAVCodecParameters(codecx.MimeTypeAmrNB), codecx.NewAvCodecParameters(codecx.ChannelLayout(codecx.Mono()),
			codecx.SampleRate(codecx.SampleRate8k), codecx.SampleFormat(codecx.AVSampleFormatS16)))
	} else if codecType == CodecAmrWB || codecType == CodecAmrWBOctet {
		aDecoder, err = codecx.NewDecoder(codecx.MimeForAVCodecParameters(codecx.MimeTypeAmrWB), codecx.NewAvCodecParameters(codecx.ChannelLayout(codecx.Mono()),
			codecx.SampleRate(codecx.SampleRate16k), codecx.SampleFormat(codecx.AVSampleFormatS16)))
	} else {
		return nil, errors.New("not support codec")
	}
	if err != nil {
		return nil, err
	}
	track := &Track{aDecoder: aDecoder}
	err = track.init(opts...)
	return track, err
}

func (t *Track) init(opt ...InBandOption) error {
	for _, o := range opt {
		o.apply(&t.opts)
	}
	t.inBand = NewInBand(8000)
	t.queue = make(chan []byte, 10)
	t.remainBuf = make([]byte, 2*DecodeBufSize+240)
	t.exit = make(chan struct{})
	// 启动后台媒体数据处理协程
	go t.process()
	return nil
}

func (t *Track) TrySend(audio []byte) bool {
	if atomic.LoadInt32(&t.sema) < 10 {
		aPacket := make([]byte, len(audio))
		copy(aPacket, audio)
		t.queue <- aPacket
		atomic.AddInt32(&t.sema, 1)
		return true
	}
	return false
}

func (t *Track) process() {
	defer func() {
		if v := recover(); v != nil && t.opts.Logger != nil {
			t.opts.Logger.WarnK(t.opts.SessionId, "Recovered from writeAudioRecord with %v, stack: %v", v, process.Stack())
		}
	}()

	for {
		select {
		case <-t.exit:
			_ = t.aDecoder.Close()
			if t.opts.Logger != nil {
				t.opts.Logger.InfoK(t.opts.SessionId, "<<DTMFTrack exit!!!>")
			}
			return
		case audio := <-t.queue:
			atomic.AddInt32(&t.sema, -1)
			// 音频数据转pcm
			frames, err := t.aDecoder.Transform(audio)
			if err != nil {
				if t.opts.Logger != nil {
					t.opts.Logger.DebugK(t.opts.SessionId, "DTMFTrackProcess decode audio without result, length: %v, cause: %v", len(frames), err)
				}
			}
			// 带内检测
			for _, packet := range frames {
				if t.pos != 0 {
					t.pos += copy(t.remainBuf[t.pos:], packet)
					packet = t.remainBuf[:t.pos]
					t.pos = 0
				}
				n, message := t.inBand.Detect(packet)
				if len(message) != 0 {
					if t.opts.Logger != nil {
						t.opts.Logger.InfoK(t.opts.SessionId, "DTMFTrackProcess detect a result: %v", message)
					}
					t.callback(message)
				}
				if n != len(packet) {
					copy(t.remainBuf, packet[n:])
					t.pos = len(packet) - n
				}
			}
		}
	}
}

func (t *Track) SetCallback(callback func(message string)) {
	t.callback = callback
}

func (t *Track) Close() {
	t.Do(func() {
		close(t.exit)
	})
}