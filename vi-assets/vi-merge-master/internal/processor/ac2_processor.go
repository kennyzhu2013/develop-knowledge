package processor

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"vi-merge/internal/metrics"
	"vi-merge/internal/models"
	"vi-merge/internal/notify"
	"vi-merge/internal/pkg/broker"
	"vi-merge/internal/pkg/ff"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/pkg/wavbox"
	"vi-merge/internal/util"
)

const (
	BytesBufferLimit = 4 * 1024 * 1024
)

var (
	localRoot       = "/data"
	NodeAccessError = errors.New("node access err")
	NodeEmptyErr    = errors.New("node empty err")
	ChannelErr      = errors.New("channel num err")
	PcmEmptyError   = errors.New("src pcm is empty")
	LocalStoreError = errors.New("upload fail and save local")

	RecordSuffixWav = "wav"
	RecordSuffixMp3 = "mp3"
)

var bufferPool = sync.Pool{
	New: func() interface{} {
		return new(bytes.Buffer)
	},
}

type WavEndCallback func(isClose bool)

type WavAc2Processor struct {
	SessionId string
	exit      chan struct{}
	dataChan  chan models.MergeChan

	segId   uint32
	bHeader models.HeaderParams

	noticeUrl string

	//记录双声道文件的长度，用于计算时长
	outPcmLen int

	// 声道数量
	channelNum int

	// 合音文件后缀
	finalSuffix string

	// 回调接口，需要回调到上一层。
	wavEndCallback WavEndCallback

	// 第一个分片长度
	t1FragmentLen int
	// 非第一分片长度
	oneFragmentLen int

	warnCnt int

	fileStore *FileStore
	once      sync.Once
	wg        sync.WaitGroup
}

func NewWavAc2Processor() *WavAc2Processor {

	return &WavAc2Processor{
		exit:      make(chan struct{}),
		dataChan:  make(chan models.MergeChan, 250),
		segId:     0,
		outPcmLen: 0,
		warnCnt:   0,
		fileStore: NewFileStore(),
	}
}

func (w *WavAc2Processor) Init(sessionId string, bHeader models.HeaderParams, notice string, wavEndCallback WavEndCallback) {
	w.SessionId = sessionId
	w.bHeader = bHeader
	w.noticeUrl = notice
	w.wavEndCallback = wavEndCallback
	dateStr, _ := util.FileDate(bHeader.StartTime)
	w.fileStore.Init(sessionId, dateStr)
	w.finalSuffix = RecordSuffixWav

	if bHeader.Source == SOURCE_BOTH {
		w.channelNum = 2
	} else {
		w.channelNum = 1
	}

	log.InfoK(w.SessionId, "WavAc2Processor,Init,channelNum=%v", w.channelNum)
	w.async(w.process)
}

func (w *WavAc2Processor) async(fn func()) {
	w.wg.Add(1)
	go func() {
		defer w.wg.Done()
		fn()
	}()
}

func (w *WavAc2Processor) Write(mergeChan models.MergeChan) error {
	select {
	case w.dataChan <- mergeChan:
	default:
		w.warnCnt++
		if w.warnCnt%5 == 0 {
			log.WarnK(w.SessionId, "WavAc2Processor,Write,drop data because the channel is full")
		}
		metrics.Pcm2MixChanFullInc(models.MergeWav)
		return errors.New("dataChan full")
	}
	return nil
}

func (w *WavAc2Processor) process() {
	pcmOutBuffer := bufferPool.Get().(*bytes.Buffer)
	defer func() {
		if pcmOutBuffer != nil {
			pcmOutBuffer.Reset()
			// 写入超大块，主动释放底层数组避免pool常驻巨量内存
			if cap(pcmOutBuffer.Bytes()) > BytesBufferLimit {
				pcmOutBuffer = nil
			}
		}

		if pcmOutBuffer != nil {
			bufferPool.Put(pcmOutBuffer)
		}

		if v := recover(); v != nil {
			log.WarnK(w.SessionId, "WavAc2Processor,process,defer,v=%v", v)
		}
	}()

	pcmOutBuffer.Reset()
	pcmOutBuffer.Grow(32000)

	w.t1FragmentLen = w.calFragmentLen(true)
	w.oneFragmentLen = w.calFragmentLen(false)
	sessionId := w.SessionId
	log.InfoK(sessionId, "WavAc2Processor,processStart,t1FragmentLen=%v,oneFragmentLen=%v",
		w.t1FragmentLen, w.oneFragmentLen)

	var err error
	var pcmBytes []byte
	var fmtParams wavbox.FmtParams
	for {
		select {
		case <-w.exit:
			log.InfoK(sessionId, "WavAc2Processor,process,EventEOF")
			return
		case dataChan, readOk := <-w.dataChan:
			if readOk {
				log.DebugK(sessionId, "WavAc2Processor,process,dataChan,leftLen=%v,rightLen=%v,Event=%v", len(dataChan.LeftPcm), len(dataChan.RightPcm), dataChan.Event)
				err = nil
				pcmBytes = nil

				fmtType := wavbox.GetFmtType(w.bHeader.Fmt)
				err, pcmBytes, fmtParams = w.pcmMix(dataChan, fmtType)
				if errors.Is(err, ChannelErr) {
					continue
				}

				if err != nil {
					log.WarnK(sessionId, "WavAc2Processor,process,pcmMix,err=%v", err)
				} else {
					lenPcmBytes := len(pcmBytes)
					log.DebugK(sessionId, "WavAc2,pcmMix Suc,len=%v", lenPcmBytes)
					var writeN int
					writeN, err = pcmOutBuffer.Write(pcmBytes)
					//writeN, err = w.wavBuf.Write(pcmBytes)
					if err != nil {
						log.WarnK(sessionId, "WavAc2Processor,process,Write ,lenData=%v,writeN=%v,err=%v", lenPcmBytes, writeN, err)
						continue
					}

					w.outPcmLen += lenPcmBytes
					if len(pcmBytes) != writeN {
						log.WarnK(sessionId, "WavAc2Processor,process,Write Size,lenData=%v,writeN=%v", len(pcmBytes), writeN)
					}

					pcmBufLen := pcmOutBuffer.Len()
					log.DebugK(sessionId, "WavAc2,process,pcmOutBuffer=%v", pcmBufLen)

					oneFragmentLen := w.fragmentLen()
					isWsEnd := dataChan.Event == models.EVENT_END || dataChan.Event == models.EVENT_END_INTERNAL
					if pcmBufLen >= oneFragmentLen || isWsEnd {
						log.DebugK(sessionId, "WavAc2,merge,isWsEnd=%v,pcmOutBuffer=%v", isWsEnd, pcmBufLen)
						pcmAc2DataAll, _ := w.readBuffer(pcmOutBuffer, pcmBufLen)
						localSegId := w.segId
						if isWsEnd {
							//结束时，不启协程，直接同步保存和上传
							w.mergeWavOutbound(isWsEnd, localSegId, pcmAc2DataAll, fmtParams)
						} else {
							//TODO: 考虑并发引起的乱序情况
							go w.mergeWavOutbound(isWsEnd, localSegId, pcmAc2DataAll, fmtParams)
						}
						//分片Id自增
						w.segId++
					}

					if isWsEnd {
						log.InfoK(sessionId, "WavAc2Processor,process,EVENT_END,return")
						//文件清理
						w.fileStore.cleanFullMergeFile()
						return
					}
				}
			} else {
				log.InfoK(sessionId, "WavAc2Processor,process,readOk=false,EventEOF")
			}
		}
	}
}

func (w *WavAc2Processor) pcmMix(dataChan models.MergeChan, fmtType wavbox.FmtType) (error, []byte, wavbox.FmtParams) {
	leftPcms := dataChan.LeftPcm
	if leftSize := len(leftPcms); leftSize > 0 && leftSize%2 != 0 {
		leftPcms = append(leftPcms, byte(0x00))
	}

	rightPcms := dataChan.RightPcm
	if rightSize := len(rightPcms); rightSize > 0 && rightSize%2 != 0 {
		rightPcms = append(rightPcms, byte(0x00))
	}

	var err error
	var pcmBytes []byte
	var fmtParams wavbox.FmtParams
	if w.channelNum == 2 {
		fmtParams, pcmBytes, err = wavbox.Pcm2Stereo(leftPcms, rightPcms, fmtType)
	} else if w.channelNum == 1 {
		if len(leftPcms) > 0 {
			pcmBytes = leftPcms
		} else if len(rightPcms) > 0 {
			pcmBytes = rightPcms
		}
		fmtParams = wavbox.GetFmtParams(fmtType)
	} else {
		err = ChannelErr
	}
	return err, pcmBytes, fmtParams
}

func (w *WavAc2Processor) calFragmentLen(isT1 bool) int {
	frameLen := w.channelFrameLen()

	// 第一合音使用起始时长，之后的用户分片时长
	var dur int
	if isT1 {
		dur = w.bHeader.InitialSegments
		if dur < 1 {
			dur = w.bHeader.Segments
		}
	} else {
		dur = w.bHeader.Segments
	}
	//100 = 50*2 双声道文件
	oneFragmentLen := dur * frameLen * 50 * w.channelNum
	return oneFragmentLen
}

func (w *WavAc2Processor) fragmentLen() int {
	if w.segId < 1 {
		return w.t1FragmentLen
	}
	return w.oneFragmentLen
}

func (w *WavAc2Processor) mergeWavOutbound(callEnd bool, segId uint32, pcmAc2DataAll []byte, fmtParams wavbox.FmtParams) {
	defer func() {
		pcmAc2DataAll = nil
		if v := recover(); v != nil {
			log.WarnK(w.SessionId, "WavAc2Processor,mergeWavOutbound,defer,v=%v", v)
		}
	}()

	netWavName := w.getNextWavName(segId)
	node, err, _ := w.mergeAudioUpload(fmtParams, pcmAc2DataAll, "", netWavName, false)
	if err == nil {
		// 发送mq消息,分片成功就可以发送消息了
		w.pubMessageMQ(w.SessionId, segId, node, netWavName, w.getDur(), callEnd, false, RecordSuffixWav)
	} else {
		log.WarnK(w.SessionId, "WavAc2Processor,mergeWavUpload,wav=%v,err=%v", netWavName, err)
	}

	// 录音全文件落盘在本地，每次追加写
	w.fileStore.recordPcm(pcmAc2DataAll)
	if callEnd {
		isMp3 := false
		log.InfoK(w.SessionId, "WavAc2Processor,finalWav,start")
		finalName := w.getFinalAudioName()
		node, isMp3, err = w.fileStore.finalFullAudio(finalName, fmtParams, w.mergeAudioUpload)

		if err != nil {
			log.WarnK(w.SessionId, "WavAc2Processor,finalWav,err=%v", err)
		} else {
			log.InfoK(w.SessionId, "WavAc2Processor,finalWav,node=%v,isMp3=%v", node, isMp3)
			// 暂时不需要去通知
			// suffix := RecordSuffixWav
			// if isMp3 {
			//	suffix = RecordSuffixMp3
			// }
			// w.pubMessageMQ(w.SessionId, segId, node, finalName, w.getDur(), true, true, suffix)
		}
	}

	if w.wavEndCallback != nil {
		w.wavEndCallback(false)
	}
}

func (w *WavAc2Processor) readBuffer(pcmOutBuffer *bytes.Buffer, pcmBufLen int) ([]byte, error) {
	if pcmBufLen < 1 {
		return nil, errors.New("lenPcmBuffLen<1")
	}

	pcmAc2DataAll := make([]byte, pcmBufLen)
	readN, err := pcmOutBuffer.Read(pcmAc2DataAll)
	if err != nil {
		log.WarnK(w.SessionId, "WavAc2Processor,mergeWavOutbound,wavBuf Read,err=%v", err)
		return nil, err
	}

	if pcmBufLen != readN {
		log.WarnK(w.SessionId, "WavAc2Processor,mergeWavOutbound,len(wavBufAll)=%v,wavBufLen=%v", readN, pcmBufLen)
	}
	return pcmAc2DataAll, err
}

func (w *WavAc2Processor) mergeAudioUpload(fmtParams wavbox.FmtParams, pcmAc2DataAll []byte, filePath string, fileName string, isFinal bool) (string, error, bool) {
	node := ""
	var audioAc2FragmentData []byte
	var err error
	var finalMp3 string
	defer func() {
		if len(node) > 1 && len(finalMp3) > 1 {
			os.Remove(finalMp3)
		}
	}()

	code := ff.ShFail
	if isFinal && len(filePath) > 1 {
		sample := w.bHeader.SampleRate
		finalMp3 = util.ChangeExt(filePath, RecordSuffixMp3)
		code = ff.Pcm2Mp3Ac2(w.SessionId, w.channelNum, sample, sample, RecordSuffixMp3, filePath, finalMp3)
		log.InfoK(w.SessionId, "WavAc2Processor,Pcm2Mp3Ac2,finalMp3=%v,code=%v", finalMp3, code)
	}

	if code == ff.ShSuc {
		audioAc2FragmentData, err = util.ReadLargeFileByBlock(finalMp3, ReadBufSize)
		if err != nil {
			log.WarnK(w.SessionId, "WavAc2Processor,ReadLargeFileByBlock,err=%v", err)
			code = ff.ShFail
		} else {
			fileName = util.ChangeExt(fileName, RecordSuffixMp3)
			log.DebugK(w.SessionId, "WavAc2Processor,Mp3Ac2,fileName=%v", fileName)
		}
	}

	if code != ff.ShSuc {
		audioAc2FragmentData, err = w.merge(fmtParams, pcmAc2DataAll)
		if err != nil {
			log.WarnK(w.SessionId, "WavAc2Processor,merge,err=%v", err)
			return "", PcmEmptyError, false
		}
	}

	path := GetPath(w.bHeader)
	node, err = w.fileStore.SelectNodeUpload(audioAc2FragmentData, path, fileName, isFinal)
	if err != nil {
		log.WarnK(w.SessionId, "WavAc2Processor,process,selectNodeUpload,err=%v", err)
		if exist, _ := util.FileExists(finalMp3); !exist {
			if isFinal {
				// 可选择不去存磁盘,分片不需要了，保留最后的总录音
				var finalFilePath string
				finalFilePath, err = w.fileStore.saveDisk(audioAc2FragmentData, path, fileName)
				log.InfoK(w.SessionId, "WavAc2Processor,saveDisk,wavFilePath=%v,err=%v", finalFilePath, err)
			}
			// 分片暂时不处理
			// path += "-fragment"
		}

		errStr := fmt.Sprintf("file upload fail,err=%v", err)
		// 通知media异常
		notify.HttpNotifyMedia(w.SessionId, w.noticeUrl, errStr)
		return node, LocalStoreError, code == ff.ShSuc
	}
	return node, nil, code == ff.ShSuc
}

func (w *WavAc2Processor) merge(fmtParams wavbox.FmtParams, pcmAc2DataAll []byte) ([]byte, error) {
	headWav, err := wavbox.GetWavHead(len(pcmAc2DataAll), fmtParams.CompressionCode, w.channelNum, w.bHeader.SampleRate, fmtParams.BitsPerSample)
	if err != nil {
		log.WarnK(w.SessionId, "WavAc2Processor,process,GetWavHead,err=%v", err)
		return nil, err
	}
	ac2FragmentData := append(headWav, pcmAc2DataAll...)
	return ac2FragmentData, nil
}

func (w *WavAc2Processor) getDur() float64 {
	var dur float64
	//双声道的channelNum=2
	if w.bHeader.SampleRate == 16000 {
		dur = float64(w.outPcmLen) / float64(32000*w.channelNum)
	} else {
		dur = float64(w.outPcmLen) / float64(16000*w.channelNum)
	}
	return dur
}

func (w *WavAc2Processor) pubMessageMQ(sessionId string, segId uint32, fileNode string, name string, dur float64, end bool, full bool, suffix string) {
	//log.InfoK(sessionId, "Publish PubBillMessage start,segId=%v,dur=%v,fileNode=%v", w.segId, dur, fileNode)
	metrics.CallFragmentInc(models.MergeWav)

	segID := &segId
	if full {
		segID = nil
	}

	uploadOk := len(fileNode) > 1
	billMessage := broker.BillMessage{
		SessionId:  sessionId,
		SegmentID:  segID,
		CallerNum:  w.bHeader.Caller,
		CalleeNum:  w.bHeader.Callee,
		UserId:     w.bHeader.UserId,
		FileNode:   fileNode,
		EndFlag:    end,
		IsStream:   true,
		Duration:   dur,
		Path:       GetPath(w.bHeader),
		FileName:   name,
		Volume:     w.fileStore.volume,
		Suffix:     suffix,
		UserData:   w.bHeader.UserDataText,
		UploadFlag: uploadOk,
		FullAudio:  full,
	}

	// Push MQ
	if err := broker.PubBillMessage(sessionId, &billMessage); err != nil {
		metrics.Send2BillFailInc(models.MergeWav)
		log.WarnK(sessionId, "Publish PubBillMessage failed, message: %v, cause: %v", billMessage.String(), err)
	}
}

func (w *WavAc2Processor) channelFrameLen() int {
	times := (w.bHeader.SampleRate * 1.0) / (8000 * 1.0)
	if times < 0 {
		times = 1.0
	}
	frameLen := times * 320
	return frameLen
}

func (w *WavAc2Processor) getNextWavName(segId uint32) string {
	//定义文件名字callId_segId.wav  20260903:Segment_sessionId_idx.suffix
	name := fmt.Sprintf("Segment_%v_%v.%v", w.SessionId, segId, RecordSuffixWav)
	return name
}

func (w *WavAc2Processor) getFinalAudioName() string {
	//dir := filepath.Join(w.bHeader.UserId, w.SessionId)
	st, err := util.FileTime(w.bHeader.StartTime)
	if err != nil {
		st = strings.ReplaceAll(st, " ", "")
		st = strings.ReplaceAll(st, ":", "")
	}
	// 这里先生成wav，再根据配置判断是否生成mp3
	name := fmt.Sprintf("%v_%v.%v", w.SessionId, st, RecordSuffixWav)
	return name
}

func (w *WavAc2Processor) Close() {
	log.InfoK(w.SessionId, "WavAc2Processor,Close")
	w.once.Do(func() {
		if w.exit != nil {
			log.InfoK(w.SessionId, "WavAc2Processor,Close exit")
			close(w.exit)
		}
	})

}
