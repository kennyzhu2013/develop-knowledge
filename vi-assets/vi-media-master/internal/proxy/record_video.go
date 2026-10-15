package proxy

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"errors"
	"fmt"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/cache"
	"vi-media/internal/pkg/log"
	"vi-media/pkg/format/record"
)

var errEmptyVRecord = errors.New("empty video record")

type videoRecord struct {
	caller record.AVMedia
	callee record.AVMedia
}

func (r *videoRecord) Update(session *Session) {
	if r.caller != nil || r.callee != nil {
		return
	}
	video, err := record.New(session.videoMimeType, fmt.Sprintf("%s_%s_%s_%s", RecordingIdentifierOriginCaller, session.id, session.callerNum, session.calleeNum),
		record.Id(session.id), record.GId(session.appId), record.Volume(conf.App.Storage.Data.Mem.Call), record.TimeStamp(session.activeTime))
	if err != nil {
		log.WarnK(session.id, "VideoRecord update caller failed: %v", err)
	}
	r.caller = video
	video, err = record.New(session.videoMimeType, fmt.Sprintf("%s_%s_%s_%s", RecordingIdentifierOriginCallee, session.id, session.callerNum, session.calleeNum),
		record.Id(session.id), record.GId(session.appId), record.Volume(conf.App.Storage.Data.Mem.Call), record.TimeStamp(session.activeTime))
	if err != nil {
		log.WarnK(session.id, "VideoRecord update callee failed: %v", err)
	}
	r.callee = video
}

func (r *videoRecord) MWrite(session *Session, rtp []byte) error {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(session.id, "Recovered from videoRecord mWritten with %v, stack: %v", v, process.Stack())
		}
	}()
	if r.caller != nil {
		_, _, _, err := r.caller.Write(rtp)
		return err
	}
	return errEmptyVRecord
}

func (r *videoRecord) SWrite(session *Session, rtp []byte) error {
	defer func() {
		if v := recover(); v != nil {
			log.WarnK(session.id, "Recovered from videoRecord sWritten with %v, stack: %v", v, process.Stack())
		}
	}()
	if r.callee != nil {
		_, _, _, err := r.callee.Write(rtp)
		return err
	}
	return errEmptyVRecord
}

func (r *videoRecord) Close(session *Session) {
	if r.caller != nil {
		if _, err := r.caller.Close(); err != nil {
			log.WarnK(session.id, "VideoRecord close failed: %v", err)
		}
	}
	if r.callee != nil {
		if _, err := r.callee.Close(); err != nil {
			log.WarnK(session.id, "VideoRecord close failed: %v", err)
		}
	}
}

func (r *videoRecord) Cancel(session *Session) {
	if session.NoRecording() {
		return
	}
	r.Close(session)
	if r.caller != nil {
		err := cache.Add(cache.ZoneC, conf.App.Storage.Data.Mem.Call, r.caller.Path(), r.caller.Name())
		if err != nil {
			log.WarnK(session.id, "[%v] Add %v to cache failed: %v", cache.ZoneC, r.caller.Name(), err)
		} else {
			log.InfoK(session.id, "[%v] Add %v to cache", cache.ZoneC, r.caller.Name())
		}
	}
	if r.callee != nil {
		err := cache.Add(cache.ZoneC, conf.App.Storage.Data.Mem.Call, r.callee.Path(), r.callee.Name())
		if err != nil {
			log.WarnK(session.id, "[%v] Add %v to cache failed: %v", cache.ZoneC, r.callee.Name(), err)
		} else {
			log.InfoK(session.id, "[%v] Add %v to cache", cache.ZoneC, r.callee.Name())
		}
	}
}

func (r *videoRecord) CallerFilename() string {
	if r.caller != nil {
		return r.caller.Name()
	}
	return ""
}

func (r *videoRecord) CalleeFilename() string {
	if r.callee != nil {
		return r.callee.Name()
	}
	return ""
}

func (r *videoRecord) Reset() {
	r.caller, r.callee = nil, nil
}