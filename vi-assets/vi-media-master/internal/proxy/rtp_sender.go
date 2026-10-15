package proxy

import (
	"context"
	"net"
	"time"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/mediasource"
	"vi-media/pkg/rtp"
	"vi-media/pkg/rtp/codec"
)

const (
	InstructionRebind = 1
	InstructionSwitch = 2
)

var (
	muteG729 = []byte{0x60, 0x5e, 0x6F, 0x40, 0x00, 0xFA, 0xC1, 0xEF, 0x7A, 0x56, 0x60, 0x5e, 0x6F, 0x40, 0x00, 0xFA, 0xC1, 0xEF, 0x7A, 0x56}
	mutePcma = []byte{
		213, 85, 213, 85, 213, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213,
		85, 213, 213, 85, 213, 85, 213, 85, 213, 213, 213, 213, 85, 213, 85, 213, 85, 85, 213, 85, 213,
		85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213,
		85, 213, 85, 213, 85, 213, 85, 85, 213, 85, 213, 85, 213, 85, 213, 85, 213, 85, 85, 213, 85, 213,
		85, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213,
		213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85,
		85, 213, 85, 213, 213, 213, 85, 213, 85, 213, 213, 213, 85, 213, 213, 85, 213, 213, 85, 213, 85,
		213, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213,
	}
	mutePcmu = []byte{
		255, 126, 255, 126, 255, 255, 126, 255, 255, 126, 255, 126, 255, 126, 254, 126, 254, 126, 255, 255, 126, 255, 255,
		126, 255, 126, 255, 126, 255, 255, 255, 255, 126, 255, 126, 254, 126, 126, 254, 126, 254, 126, 255, 255, 126, 254,
		126, 254, 126, 254, 126, 255, 255, 126, 254, 126, 254, 126, 255, 255, 126, 254, 125, 254, 125, 254, 126, 254, 126,
		126, 254, 126, 255, 126, 255, 126, 254, 126, 254, 126, 126, 254, 126, 254, 126, 255, 126, 255, 126, 254, 126, 255,
		126, 255, 255, 126, 254, 126, 255, 126, 255, 126, 255, 255, 126, 255, 255, 126, 254, 126, 254, 126, 254, 126, 255,
		255, 126, 255, 126, 255, 126, 255, 126, 255, 255, 126, 254, 126, 126, 255, 126, 255, 255, 255, 126, 255, 126, 255,
		255, 255, 126, 255, 255, 126, 255, 255, 126, 255, 126, 255, 255, 126, 255, 126, 255, 126, 255, 255, 126, 255,
	}
	muteAmrNB = []byte{243, 230, 110, 172, 190, 181, 254, 112, 112, 33, 249, 123, 249, 153, 17, 216, 176, 0, 8, 38, 178, 101, 243, 180, 0, 1, 39, 81, 248, 56, 145, 224}
	muteAmrWB = []byte{
		132, 84, 17, 136, 12, 239, 179, 173, 89, 64, 156, 0, 106, 87, 3, 167, 63, 179, 44, 17, 193, 1, 2, 232, 1, 49, 193,
		241, 72, 38, 4, 79, 13, 234, 20, 253, 156, 23, 148, 1, 148, 224, 31, 193, 164, 4, 108, 148, 8, 76, 57, 167, 92, 151, 194, 252, 250, 194, 139, 11, 44,
	}
	muteAmrNBOctet = []byte{240, 60, 153, 186, 178, 250, 215, 249, 193, 192, 135, 229, 239, 230, 100, 71, 98, 192, 0, 32, 154, 201, 151, 206, 208, 0, 4, 157, 71, 224, 226, 71, 128}
	muteAmrWBOctet = []byte{
		128, 68, 49, 70, 16, 17, 151, 71, 176, 82, 96, 187, 205, 5, 12, 230, 102, 119, 235, 154, 53, 12, 54, 59, 252, 3,
		98, 165, 109, 4, 226, 74, 89, 55, 160, 219, 98, 68, 30, 217, 123, 96, 140, 2, 2, 232, 112, 55, 161, 23, 65, 14, 37, 125, 173, 101, 179, 247, 113, 203, 43, 184}

	muteCache = map[string][]byte{
		codec.MimeTypeAmrNB:      muteAmrNB,
		codec.MimeTypeAmrNBOctet: muteAmrNBOctet,
		codec.MimeTypeAmrWB:      muteAmrWB,
		codec.MimeTypeAmrWBOctet: muteAmrWBOctet,
		codec.MimeTypePCMA:       mutePcma,
		codec.MimeTypePCMU:       mutePcmu,
		codec.MimeTypeG729:       muteG729,
	}
)

type AvType interface {
	[][]byte | []*codec.Fragment
}

type instruction struct {
	iType int
	data  any
}

type rebinding struct {
	trackId  string
	bindId   string
	mimeType string
	payload  uint8
	addr     *net.UDPAddr
}

type avSwitch[T AvType] struct {
	trackId  string
	data     T
	loopData T
}

type trackData[T AvType] struct {
	id       string
	data     T
	loopData T
}

type rtpSender struct {
	session *Session
	// The rtp send track
	track []rtp.ITrack
	// The instruction channel, receiving external commands to control,
	// mainly including changes in media connection information, media switching interruptions, etc.
	instr chan instruction
	// The exit control channel
	ctx    context.Context
	cancel context.CancelFunc
}

func newRtpSender(session *Session) *rtpSender {
	ctx, cancel := context.WithCancel(session.ctx)
	return &rtpSender{session: session, ctx: ctx, cancel: cancel}
}

func (s *rtpSender) Bind(trackId, mimeType string, dataConn *net.UDPConn, tCtx rtp.TrackContext) error {
	track := s.findTrack(trackId)
	if track != nil {
		return track.Bind(tCtx)
	}
	track = rtp.NewTrack(trackId, mimeType, dataConn)
	if err := track.Bind(tCtx); err != nil {
		return err
	}
	s.track = append(s.track, track)
	if s.instr == nil {
		s.instr = make(chan instruction, 4)
	}
	return nil
}

func (s *rtpSender) SendAudio(trackId string, audio [][]byte, opt ...SendOption) {
	track := s.findTrack(trackId)
	if track == nil {
		return
	}
	avParameters, ok := codec.Find(track.MimeType())
	if !ok {
		return
	}
	opts := new(SendOptions)
	for _, o := range opt {
		o(opts)
	}
	// 20ms per frame
	ticker := time.NewTicker(rtp.AudioInterval)
	// One RTP packet for the entire
	packet := rtp.NewPacket()

	var err error
	timestamp, timeInterval := rtp.NewTimestamp(), avParameters.ClockRate/avParameters.FrameRate
	loopAudio, _ := opts.LoopData.([][]byte)
	loop := len(loopAudio) != 0

Loop:
	for idx := 0; idx < len(audio); {
		select {
		case <-s.ctx.Done():
			break Loop
		case instr := <-s.instr:
			switch instr.iType {
			case InstructionRebind:
				rebind(s, instr.data.(rebinding), &timeInterval)
			case InstructionSwitch:
				audio = switchMediaSource(instr.data.(avSwitch[[][]byte]), opts, loop)
			}
		case <-ticker.C:
			packet.SetTimestamp(timestamp)
			packet.SetPayload(audio[idx])
			err = track.WriteP(s.ctx, packet)
			log.DebugK(s.session.id, "Send audio, %v, seq: %v, timestamp: %v, result: %v", track.Bindings(), packet.Sequence(), packet.Timestamp(), err)
			if opts.Process != nil {
				_ = opts.Process(mediasource.SourceEntire, packet.NoCopy())
			}
			timestamp += timeInterval
			idx++
			if idx >= len(audio) {
				if loop {
					if len(loopAudio) != 0 {
						audio = loopAudio
						loopAudio, opts.LoopData = nil, nil
					}
					idx = 0
				}
				if opts.FinalizeProcess != nil {
					opts.FinalizeProcess()
					opts.FinalizeProcess = nil
				}
			}
		}
	}
	rtp.FreePacket(packet)
	ticker.Stop()
	// Callback
	if opts.Callback != nil {
		opts.Callback()
	}
	if opts.WaitHandle != nil {
		opts.WaitHandle.Done()
	}
}

func (s *rtpSender) SendVaryAudio(data []trackData[[][]byte], opt ...SendOption) {
	if len(data) < 2 {
		return
	}
	maxIdx, maxLen, loop := 0, 0, false
	idx := make([]int, len(data))
	tracks := make([]rtp.ITrack, len(data))
	timestamp := make([]uint32, len(data))
	timeInterval := make([]uint32, len(data))
	for i, td := range data {
		track := s.findTrack(td.id)
		if track == nil {
			return
		}
		tracks[i] = track
		avParameters, ok := codec.Find(track.MimeType())
		if !ok {
			return
		}
		timestamp[i] = rtp.NewTimestamp()
		timeInterval[i] = avParameters.ClockRate / avParameters.FrameRate
		if len(td.data) > maxLen {
			maxIdx = i
			maxLen = len(td.data)
		}
		if len(td.loopData) != 0 {
			loop = true
		}
	}
	opts := new(SendOptions)
	for _, o := range opt {
		o(opts)
	}
	// 20ms per frame
	ticker := time.NewTicker(rtp.AudioInterval)
	// One RTP packet for the entire
	packet := rtp.NewPacket()

Loop:
	for idx[maxIdx] < maxLen || loop {
		select {
		case <-s.ctx.Done():
			break Loop
		case instr := <-s.instr:
			switch instr.iType {
			case InstructionRebind:
				rebindVary(instr.data.(rebinding), tracks, timeInterval)
			case InstructionSwitch:
				switchVaryMediaSource(instr.data.(avSwitch[[][]byte]), tracks, data, loop)
			}
		case <-ticker.C:
			for i := range tracks {
				if idx[i] >= len(data[i].data) {
					packet.SetPayload(muteCache[tracks[i].MimeType()])
				} else {
					packet.SetPayload(data[i].data[idx[i]])
				}
				packet.SetTimestamp(timestamp[i])
				err := tracks[i].WriteP(s.ctx, packet)
				log.DebugK(s.session.id, "Send vary audio, %v, seq: %v, timestamp: %v, result: %v", tracks[i].Bindings(), packet.Sequence(), packet.Timestamp(), err)
				if opts.Process != nil {
					_ = opts.Process(i+1, packet.NoCopy())
				}
				timestamp[i] += timeInterval[i]
				idx[i]++
				if idx[i] >= len(data[i].data) {
					if loop {
						if len(data[i].loopData) != 0 {
							data[i].data = data[i].loopData
							data[i].loopData = nil
						}
						idx[i] = 0
					}
				}
			}
		}
	}
	rtp.FreePacket(packet)
	ticker.Stop()
	// Callback
	if opts.Callback != nil {
		opts.Callback()
	}
	if opts.WaitHandle != nil {
		opts.WaitHandle.Done()
	}
}

func (s *rtpSender) SendVideo(trackId string, video []*codec.Fragment, opt ...SendOption) {
	track := s.findTrack(trackId)
	if track == nil {
		return
	}
	avParameters, ok := codec.Find(track.MimeType())
	if !ok {
		return
	}
	opts := new(SendOptions)
	for _, o := range opt {
		o(opts)
	}
	ticker := time.NewTicker(time.Duration(1000/avParameters.FrameRate) * time.Millisecond)
	packet := rtp.NewPacket()

	var err error
	timestamp, timeInterval := rtp.NewTimestamp(), avParameters.ClockRate/avParameters.FrameRate
	loopVideo, _ := opts.LoopData.([]*codec.Fragment)
	loop := len(loopVideo) != 0

Loop:
	for idx := 0; idx < len(video); {
		fg := video[idx]
		select {
		case <-s.ctx.Done():
			break Loop
		case instr := <-s.instr:
			switch instr.iType {
			case InstructionRebind:
				rebind(s, instr.data.(rebinding), &timeInterval)
			case InstructionSwitch:
				video = switchMediaSource(instr.data.(avSwitch[[]*codec.Fragment]), opts, loop)
			}
		default:
			if fg.FType == codec.NALUTypeNIDR || fg.FType == codec.NALUTypeIDR || fg.FType == codec.NALUTypeSEI || fg.FType == codec.NALUTypeSPS || fg.FType == codec.NALUTypePPS {
				// Set the marker to true for Non-IDR frames
				packet.SetMarker(fg.FType == codec.NALUTypeNIDR)
				packet.SetPayload(fg.Frames[0])
				packet.SetTimestamp(timestamp)
				err = track.WriteP(s.ctx, packet)
				log.DebugK(s.session.id, "Send video, %v, keyFrame: %v, frameType: %v, seq: %v, timestamp: %v, result: %v", track.Bindings(), fg.KeyFrame, fg.FType, packet.Sequence(), packet.Timestamp(), err)
				if opts.Process != nil {
					_ = opts.Process(mediasource.SourceEntire, packet.NoCopy())
				}
				if fg.FType == codec.NALUTypeNIDR || fg.FType == codec.NALUTypeIDR {
					timestamp += timeInterval
					<-ticker.C
				}
			} else if fg.FType == codec.NALUTypeFua {
				packet.SetTimestamp(timestamp)
				for j := range fg.Frames {
					packet.SetPayload(fg.Frames[j])
					// Set the marker to true for the last fragment of the FUA frames
					packet.SetMarker(j == len(fg.Frames)-1)
					err = track.WriteP(s.ctx, packet)
					log.DebugK(s.session.id, "Send video, %v, keyFrame: %v, frameType: 28, seq: %v, timestamp: %v, result: %v", track.Bindings(), fg.KeyFrame, packet.Sequence(), packet.Timestamp(), err)
					if opts.Process != nil {
						_ = opts.Process(mediasource.SourceEntire, packet.NoCopy())
					}
				}
				timestamp += timeInterval
				<-ticker.C
			}
			idx++
			if idx >= len(video) {
				if loop {
					if len(loopVideo) != 0 {
						video = loopVideo
						loopVideo, opts.LoopData = nil, nil
					}
					idx = 0
				}
				if opts.FinalizeProcess != nil {
					opts.FinalizeProcess()
					opts.FinalizeProcess = nil
				}
			}
		}
	}
	rtp.FreePacket(packet)
	ticker.Stop()
	// Callback
	if opts.Callback != nil {
		opts.Callback()
	}
	if opts.WaitHandle != nil {
		opts.WaitHandle.Done()
	}
}

func (s *rtpSender) findTrack(trackId string) rtp.ITrack {
	for _, t := range s.track {
		if t != nil && t.ID() == trackId {
			return t
		}
	}
	return nil
}

func (s *rtpSender) Stop() {
	if s.cancel != nil {
		s.cancel()
	}
}

func SwitchMediaSource[T AvType](s *rtpSender, avs avSwitch[T]) {
	select {
	case s.instr <- instruction{iType: InstructionSwitch, data: avs}:
	default:
	}
}

func Rebind(s *rtpSender, binding rebinding) {
	select {
	case s.instr <- instruction{iType: InstructionRebind, data: binding}:
	default:
	}
}

func switchMediaSource[T AvType](avs avSwitch[T], opts *SendOptions, loop bool) T {
	if loop {
		if opts.LoopData == nil {
			return avs.loopData
		}
		opts.LoopData = avs.loopData
	}
	return avs.data
}

func switchVaryMediaSource[T AvType](avs avSwitch[T], tracks []rtp.ITrack, data []trackData[T], loop bool) {
	for i := range tracks {
		if tracks[i] != nil && tracks[i].ID() == avs.trackId {
			if loop {
				if data[i].loopData == nil {
					data[i].data = avs.loopData
					return
				}
				data[i].loopData = avs.loopData
			}
			data[i].data = avs.data
			return
		}
	}
}

func rebind(s *rtpSender, binding rebinding, timeInterval *uint32) {
	track := s.findTrack(binding.trackId)
	if track == nil {
		return
	}
	mimeType := track.MimeType()
	_ = track.Rebind(rtp.TrackContext{Id: binding.bindId, PT: binding.payload, Mime: binding.mimeType, Addr: binding.addr})
	if len(binding.mimeType) != 0 && binding.mimeType != mimeType {
		if avParams, ok := codec.Find(binding.mimeType); ok {
			*timeInterval = avParams.ClockRate / avParams.FrameRate
		}
	}
}

func rebindVary(binding rebinding, tracks []rtp.ITrack, timeInterval []uint32) {
	for i := range tracks {
		if tracks[i] != nil && tracks[i].ID() == binding.trackId {
			mimeType := tracks[i].MimeType()
			_ = tracks[i].Rebind(rtp.TrackContext{Id: binding.bindId, PT: binding.payload, Mime: binding.mimeType, Addr: binding.addr})
			if len(binding.mimeType) != 0 && binding.mimeType != mimeType {
				if avParams, ok := codec.Find(binding.mimeType); ok {
					timeInterval[i] = avParams.ClockRate / avParams.FrameRate
				}
			}
			return
		}
	}
}