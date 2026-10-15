package processor

import (
	"offline-merge/internal/audioformat"
	"offline-merge/internal/httpinterface"
	"offline-merge/internal/metrics"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/ff"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/util"
	"sync"
	"time"
)

const name = DATA

const (
	MS_DOWN_BODY = "/media/download"
)

type BatchFileDown struct {
	sId string

	fileServer *httpinterface.FileServer

	wg sync.WaitGroup

	mode string // dcc or vi,

}

func NewBatchFileDown(id string, mode string) *BatchFileDown {
	fs := httpinterface.NewFileServer(id)
	return &BatchFileDown{
		sId:        id,
		fileServer: fs,
		mode:       mode,
	}
}

// TODO: return error,but need to consider getting error along goroutines
func (b *BatchFileDown) FileDown(mergeCtx *models.MergeCtx) {
	mediaUrl := mergeCtx.MergeMessage.GetMediaUrl()
	if len(mediaUrl) < 1 {
		log.WarnK(b.sId, "fileDownload,media url is empty,return")
		metrics.DownFileFailInc(b.mode, "empty url")
		return
	}

	startTime := time.Now()
	downUrl := mediaUrl + MS_DOWN_BODY
	var downLegs []*models.PairFileLeg
	callDownLegs := mergeCtx.LocalOptions.LocalLegList
	if len(callDownLegs) > 0 {
		downLegs = append(downLegs, callDownLegs...)
	}

	earlyDownLegs := mergeCtx.LocalOptions.LocalEarlyList
	if len(earlyDownLegs) > 0 {
		downLegs = append(downLegs, earlyDownLegs...)
	}

	if len(downLegs) == 0 {
		log.WarnK(b.sId, "fileDownload,earlyDownload,downLegsSize==0")
		metrics.DownFileFailInc(b.mode, "no resource of early,caller or callee to download")
		return // TODO: return is better,because there is no meaning
	}

	for _, localLeg := range downLegs {
		if localLeg == nil {
			continue
		}

		if localLeg.CallerLeg != nil {
			b.wg.Add(1)
			go b.singleDown(localLeg.CallerLeg, downUrl)
		}

		if localLeg.CalleeLeg != nil {
			b.wg.Add(1)
			go b.singleDown(localLeg.CalleeLeg, downUrl)
		}
	}

	log.InfoK(b.sId, "FileDown,Wait Down start")
	b.wg.Wait()
	spTime := time.Since(startTime).Milliseconds()
	if spTime > 1000 {
		log.WarnK(b.sId, "FileDown,Wait Down end,long,spTime=%v", spTime)
	} else {
		log.InfoK(b.sId, "FileDown,Wait Down end,spTime=%v", spTime)
	}

	metrics.MergeDownTimeObserve(mergeCtx.Mode(), float64(spTime)) // interval of downloading
}

func (b *BatchFileDown) singleDown(fileLeg *models.FileLeg, downUrl string) {
	defer func() {
		b.wg.Done()
		if v := recover(); v != nil {
			log.WarnK(b.sId, "singleDown,recover,v=%v", v)
		}
	}()

	if b.fileServer != nil {
		if len(fileLeg.LocalPath) < 10 || len(fileLeg.FileName) < 10 {
			log.InfoK(b.sId, "singleDown,file fail,fileName=%v", fileLeg.FileName)
			return
		}
		code, fileByte := b.fileServer.DownFilePost(fileLeg.LocalPath, fileLeg.FileName, downUrl)

		var err error
		var fileLen int64
		var duration float32
		var inCode int
		if code == httpinterface.FILE_SUC_CODE {
			fileL := len(fileByte)
			if fileL > 0 {
				log.InfoK(b.sId, "singleDown,buffer,path=%v", fileLeg.LocalPath)
				fileLen = int64(fileL)
				inCode = audioformat.GetHandleCodeByte(fileLeg.LocalPath, fileByte, fileLeg.PcmAttr)
				duration, err = GetAudioDur(b.sId, fileLeg.LocalPath, fileByte, inCode)
				if err != nil {
					log.WarnK(b.sId, "singleDown,GetAudioDur,err=%v,path=%v", err, fileLeg.LocalPath)
					duration = ff.GetTimeSh(b.sId, fileLeg.LocalPath, inCode)
				}
			} else {
				// Tip：FileLeg是evs,这里需要先多一步转成pcm方式再进一步处理。 转码成功之后，直接替换整个路径。
				fileLen, err = util.FileLength(fileLeg.LocalPath)
				if err != nil {
					log.WarnK(b.sId, "singleDown,path=%v", fileLeg.LocalPath)
					return
				}
				inCode = audioformat.GetHandleCode(fileLeg.LocalPath, fileLeg.PcmAttr)
				duration = ff.GetTimeSh(b.sId, fileLeg.LocalPath, inCode)
			}

			fileLeg.InCode = inCode
			fileLeg.FileLength = fileLen
			fileLeg.Duration = duration
			log.InfoK(b.sId, "downFileLeg,path=%v,fileLen=%v,inCode=%v,duration=%v", fileLeg.LocalPath, fileLen, inCode, duration)
		} else {
			// TODO: "offline" string is no meaning,because we can know that from service name
			//metrics.DownFileFailInc("offline", httpinterface.CodeName(code))
			metrics.DownFileFailInc(b.mode, httpinterface.CodeName(code))
			log.WarnK(b.sId, "downFileLeg fail,fileName=%v", fileLeg.FileName)
		}
	}

}
