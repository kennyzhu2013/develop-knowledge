package proxy

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"cmic.chinamobile.com/ziyan/go-sdk/version"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/broker"
	"vi-media/internal/pkg/cache"
	"vi-media/internal/pkg/log"
	"vi-media/internal/pkg/mediasource"
	"vi-media/pkg/format/record"
)

var errEmptyARecord = errors.New("empty audio record")

type audioRecord struct {
	caller record.AVMedia
	callee record.AVMedia
	// Statistics on the number of recordings in the calling stage
	// Used for naming calling multi-segment recording
	oCnt int
	// Calling recording name list
	mFiles string
	sFiles string
	// Calling recording list
	mime  []string
	mData [][]byte
	sData [][]byte

	// The video record handle
	// Used only when pushing recordings at the end of a call
	video *videoRecord
}

func (r *audioRecord) Update(session *Session, bEarly bool) {
	if bEarly || session.NoRecording() {
		return
	}
	// Close the prev recording
	r.close(session)
	// Early media
	// Applies only to outgoing calls
	prefix := ""
	mIdentifier, sIdentifier := RecordingIdentifierOriginCaller, RecordingIdentifierOriginCallee
	if r.oCnt > 0 {
		prefix = strconv.Itoa(r.oCnt)
	}
	if session.viSource != mediasource.SourceCallee {
		audio, err := record.New(session.audioMimeType, fmt.Sprintf("%s%s_%s", prefix, mIdentifier, session.id), record.Id(session.id), record.GId(session.appId),
			record.Volume(conf.App.Storage.Data.Mem.Call), record.TimeStamp(session.activeTime), record.InMemory(session.needVI))
		if err != nil {
			log.WarnK(session.id, "AudioRecord update caller failed: %v", err)
		}
		r.caller = audio
	}
	if session.viSource != mediasource.SourceCaller {
		audio, err := record.New(session.audioMimeType, fmt.Sprintf("%s%s_%s", prefix, sIdentifier, session.id), record.Id(session.id), record.GId(session.appId),
			record.Volume(conf.App.Storage.Data.Mem.Call), record.TimeStamp(session.activeTime), record.InMemory(session.needVI))
		if err != nil {
			log.WarnK(session.id, "AudioRecord update callee failed: %v", err)
		}
		r.callee = audio
	}
}

func (r *audioRecord) MWrite(session *Session, rtp []byte) ([]byte, int, int, error) {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(session.id, "Recovered from audioRecord mWritten with %v, stack: %v", v, process.Stack())
		}
	}()
	if r.caller != nil {
		return r.caller.Write(rtp)
	}
	return nil, 0, 0, errEmptyARecord
}

func (r *audioRecord) SWrite(session *Session, rtp []byte) ([]byte, int, int, error) {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(session.id, "Recovered from audioRecord sWritten with %v, stack: %v", v, process.Stack())
		}
	}()
	if r.callee != nil {
		return r.callee.Write(rtp)
	}
	return nil, 0, 0, errEmptyARecord
}

func (r *audioRecord) close(session *Session) {
	inc, addMime := true, true
	closeMedia := func(media record.AVMedia, filename *string, data [][]byte, mime []string) ([][]byte, []string) {
		if media == nil {
			data = append(data, nil)
			return data, mime
		}
		name := *filename
		if len(name) != 0 {
			name += ","
		}
		name += media.Name()
		*filename = name
		if inc {
			r.oCnt++
			inc = false
		}
		frames, err := media.Close()
		data = append(data, frames)
		if addMime {
			mime = append(mime, media.MimeType())
			addMime = false
		}
		if err != nil {
			log.WarnK(session.id, "AudioRecord close %v failed: %v", media.Name(), err)
		}
		return data, mime
	}
	r.mData, r.mime = closeMedia(r.caller, &r.mFiles, r.mData, r.mime)
	r.sData, r.mime = closeMedia(r.callee, &r.sFiles, r.sData, r.mime)
}

func (r *audioRecord) Flush(session *Session) {
	shard := func(data []byte, source byte, step int) {
		for i := 0; i < len(data); i += step {
			session.streamAudioProxy.outbound.Send(mediasource.KindAudio, source, 0, data[i:min(i+step, len(data))])
		}
	}
	// Send outbound
	for i := range r.mime {
		if len(r.mData[i]) == 0 && len(r.sData[i]) == 0 {
			continue
		}
		session.streamAudioProxy.outbound.Notice(mediasource.EventCodecSwitch, r.mime[i])
		if len(r.mData[i]) != 0 && len(r.sData[i]) != 0 {
			for j, k, step := 0, 0, 50*record.FrameSize(r.mime[i]); j < len(r.mData[i]) || k < len(r.sData[i]); {
				if j < len(r.mData[i]) {
					session.streamAudioProxy.outbound.Send(mediasource.KindAudio, mediasource.SourceCaller, 0, r.mData[i][j:min(j+step, len(r.mData[i]))])
					j += step
				}
				if k < len(r.sData[i]) {
					session.streamAudioProxy.outbound.Send(mediasource.KindAudio, mediasource.SourceCallee, 0, r.sData[i][k:min(k+step, len(r.sData[i]))])
					k += step
				}
			}
			continue
		}
		if len(r.mData[i]) != 0 {
			shard(r.mData[i], mediasource.SourceCaller, 50*record.FrameSize(r.mime[i]))
		} else if session.viSource != mediasource.SourceCallee {
			session.streamAudioProxy.outbound.Send(mediasource.KindAudio, mediasource.SourceCaller, record.FrameNumber(r.mime[i], len(r.sData[i]))-1, record.Mute(r.mime[i]))
		}
		if len(r.sData[i]) != 0 {
			shard(r.sData[i], mediasource.SourceCallee, 50*record.FrameSize(r.mime[i]))
		} else if session.viSource != mediasource.SourceCaller {
			session.streamAudioProxy.outbound.Send(mediasource.KindAudio, mediasource.SourceCallee, record.FrameNumber(r.mime[i], len(r.mData[i]))-1, record.Mute(r.mime[i]))
		}
	}
	r.mime, r.mData, r.sData = nil, nil, nil
	// Mark file mode
	// Flush the remaining audio cache data
	if r.caller == nil && r.callee == nil {
		return
	}
	var mime string
	var mData []byte
	var sData []byte
	if r.caller != nil {
		mime = r.caller.MimeType()
		mData, _ = r.caller.Flush()
		r.caller.MarkInMemory(false)
	}
	if r.callee != nil {
		mime = r.callee.MimeType()
		sData, _ = r.callee.Flush()
		r.callee.MarkInMemory(false)
	}
	if len(mData) != 0 && len(sData) != 0 {
		session.streamAudioProxy.outbound.Notice(mediasource.EventCodecSwitch, mime)
		for j, k, step := 0, 0, 50*record.FrameSize(mime); j < len(mData) || k < len(sData); {
			if j < len(mData) {
				session.streamAudioProxy.outbound.Send(mediasource.KindAudio, mediasource.SourceCaller, 0, mData[j:min(j+step, len(mData))])
				j += step
			}
			if k < len(sData) {
				session.streamAudioProxy.outbound.Send(mediasource.KindAudio, mediasource.SourceCallee, 0, sData[k:min(k+step, len(sData))])
				k += step
			}
		}
		return
	}
	session.streamAudioProxy.outbound.Notice(mediasource.EventCodecSwitch, mime)
	if len(mData) != 0 {
		shard(mData, mediasource.SourceCaller, 50*record.FrameSize(mime))
	}
	if len(sData) != 0 {
		shard(sData, mediasource.SourceCallee, 50*record.FrameSize(mime))
	}
}

func (r *audioRecord) FallbackVI(_ *Session) {
	if r.caller != nil {
		r.caller.MarkInMemory(false)
	}
	if r.callee != nil {
		r.callee.MarkInMemory(false)
	}
}

func (r *audioRecord) Cancel(session *Session) {
	if !session.IsInCall() || session.NoRecording() {
		return
	}
	r.close(session)
	// Calling recording
	mergeMessage := broker.MediaMergeMessage{
		SessionId:                 session.id,
		CallerNum:                 session.callerNum,
		CalleeNum:                 session.calleeNum,
		UserId:                    session.appId,
		MsId:                      conf.App.Registry.AppId,
		Address:                   conf.App.ApiAddress,
		Version:                   version.Version,
		CallerAudioFile:           r.mFiles,
		CalleeAudioFile:           r.sFiles,
		StartTime:                 session.activeTime,
		Options:                   session.mergeConfig,
		Config:                    session.viConfig,
		Fallback:                  session.fallback,
		CallerAudioRtpCount:       session.audioStats.CallerRtpCnt,
		CalleeAudioRtpCount:       session.audioStats.CalleeRtpCnt,
		CallerAudioInCallRtpCount: session.audioStats.CallerInCallRtpCnt,
		CalleeAudioInCallRtpCount: session.audioStats.CalleeInCallRtpCnt,
		CallerAudioRecordCount:    session.audioStats.CallerRecordCnt,
		CalleeAudioRecordCount:    session.audioStats.CalleeRecordCnt,
		CallerVideoRtpCount:       session.videoStats.CallerRtpCnt,
		CalleeVideoRtpCount:       session.videoStats.CalleeRtpCnt,
		CallerVideoRecordCount:    session.videoStats.CallerRecordCnt,
		CalleeVideoRecordCount:    session.videoStats.CalleeRecordCnt,
	}
	if len(r.mFiles) != 0 {
		err := cache.Add(cache.ZoneC, conf.App.Storage.Data.Mem.Call, r.caller.Path(), strings.Split(r.mFiles, ",")...)
		if err != nil {
			log.WarnK(session.id, "[%v] Add %v to cache failed: %v", cache.ZoneC, r.mFiles, err)
		} else {
			log.InfoK(session.id, "[%v] Add %v to cache", cache.ZoneC, r.mFiles)
		}
		mergeMessage.Path = r.caller.Path()
	}
	if len(r.sFiles) != 0 {
		err := cache.Add(cache.ZoneC, conf.App.Storage.Data.Mem.Call, r.callee.Path(), strings.Split(r.sFiles, ",")...)
		if err != nil {
			log.WarnK(session.id, "[%v] Add %v to cache failed: %v", cache.ZoneC, r.sFiles, err)
		} else {
			log.InfoK(session.id, "[%v] Add %v to cache", cache.ZoneC, r.sFiles)
		}
		mergeMessage.Path = r.callee.Path()
	}
	if r.video != nil {
		r.video.Cancel(session)
		mergeMessage.CallerVideoFile = r.video.CallerFilename()
		mergeMessage.CalleeVideoFile = r.video.CalleeFilename()
	}
	// Push MQ
	if session.needVI && !session.enableVI {
		return
	}
	if err := broker.PubMergeMessage(&mergeMessage); err != nil {
		log.WarnK(session.id, "Publish mediaMergeMessage failed, message: %v, cause: %v", mergeMessage.String(), err)
	} else {
		log.InfoK(session.id, "Publish mediaMergeMessage success, message: %v", mergeMessage.String())
	}
}

func (r *audioRecord) Reset() {
	r.oCnt = 0
	r.mFiles, r.sFiles = "", ""
	r.mime, r.mData, r.sData = nil, nil, nil
	r.caller, r.callee, r.video = nil, nil, nil
}