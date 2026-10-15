package processor

import (
	"bytes"
	"errors"
	"strconv"
	"time"
	"vi-merge/internal/metrics"
	"vi-merge/internal/models"
	"vi-merge/internal/pkg/log"
)

const (
	BufSize = 30 * 1024

	//1秒
	BUF_8K_TO_MERGE  = 320 * 50
	BUF_16K_TO_MERGE = 640 * 50

	//输入源：1、主叫；2、被叫；3、主被叫
	SOURCE_LEFT  = 1
	SOURCE_RIGHT = 2
	SOURCE_BOTH  = 3
)

type WsPcmProcessor struct {
	SessionId string

	leftBuf  *bytes.Buffer
	rightBuf *bytes.Buffer

	leftFrameCnt  uint
	rightFrameCnt uint

	mergeThreshold  int
	wavAc2Processor *WavAc2Processor

	// 输入源：1、主叫；2、被叫；3、主被叫，默认是1
	source uint8

	//leftWriter  *util.PcmWriter
	//rightWriter *util.PcmWriter
}

func NewWsPcmProcessor() *WsPcmProcessor {
	wsPcmProcessor := &WsPcmProcessor{
		leftFrameCnt:   0,
		rightFrameCnt:  0,
		mergeThreshold: BUF_16K_TO_MERGE,
		source:         SOURCE_LEFT,
		leftBuf:        new(bytes.Buffer),
		rightBuf:       new(bytes.Buffer),
	}

	wsPcmProcessor.init()
	return wsPcmProcessor
}

func (p *WsPcmProcessor) init() {
	p.leftFrameCnt = 0
	p.rightFrameCnt = 0
	p.leftBuf.Reset()
	p.rightBuf.Reset()
	p.leftBuf.Grow(32000)
	p.rightBuf.Grow(32000)
}

func (p *WsPcmProcessor) GetBusinessType() string {
	return models.BusinessTypeMerge
}

func (p *WsPcmProcessor) GetMessageType() string {
	return models.MergeWav
}

func (p *WsPcmProcessor) Process(sId string, bHeader *models.BusinessHeader, data interface{}) error {
	p.SessionId = sId
	//初始化合音processor
	if p.wavAc2Processor == nil {
		p.wavAc2Processor = NewWavAc2Processor()
		p.wavAc2Processor.Init(sId, bHeader.BizConfig, bHeader.Notice, p.endCallback)
		p.source = bHeader.BizConfig.Source
		if p.source > 3 {
			log.WarnK(sId, "WsPcmProcessor,Process,source reset3")
			p.source = 3
		} else if p.source < 1 {
			log.WarnK(sId, "WsPcmProcessor,Process,source reset1")
			p.source = 1
		}

		//if conf.App.LogLevel == "debug" {
		//	bufSize := 320 * 50
		//	dir, _ := util.CreateLocalDir("/data/local/", sId)
		//	leftPcm := fmt.Sprintf("%v/%v_src_left.pcm", dir, sId)
		//	rightPcm := fmt.Sprintf("%v/%v_src_right.pcm", dir, sId)
		//	p.leftWriter, _ = util.NewPcmWriter(leftPcm, bufSize)
		//	p.rightWriter, _ = util.NewPcmWriter(rightPcm, bufSize)
		//}

		//监控
		metrics.CallSourceInc(models.MergeWav, strconv.Itoa(int(p.source)))
		p.mergeThreshold = p.mergeTrigger(bHeader.BizConfig.SampleRate, bHeader.BizConfig.InitialSegments, bHeader.BizConfig.Segments)
		log.InfoK(sId, "WsPcmProcessor,mergeThreshold=%v(8k,16000/s)，source=%v", p.mergeThreshold, p.source)
	}

	content, ok := data.(*models.MergeContent)
	if !ok {
		log.WarnK(sId, "WsPcmProcessor,Process,invalid data type")
		return nil
	}

	var err error
	log.DebugK(sId, "WsPcmProcessor,content,type=%v,content.Source=%v,event=%v,len(pcm)=%v",
		content.SubType, content.Source, content.Event, len(content.Pcm))
	if content.SubType == models.WsBinaryMessage {
		if bytes.Equal(content.Pcm, models.EosByte) {
			log.InfoK(sId, "WsPcmProcessor,binary EOS")
			err = p.Flush(models.EVENT_END)
			if err != nil {
				log.WarnK(sId, "WsPcmProcessor,Flush,event=%v,err=%v", models.EVENT_END, err)
			}
			return nil
		}

		var n int
		if content.Source == SOURCE_LEFT {
			if p.leftBuf != nil {
				n, err = p.leftBuf.Write(content.Pcm)
				p.leftFrameCnt++

				//if p.leftWriter != nil {
				//	err = p.leftWriter.AppendPCM(content.Pcm)
				//	if err != nil {
				//		log.WarnK(sId, "WsPcmProcessor,Left,AppendPCM,err=%v", err)
				//	}
				//}

			}
		} else if content.Source == SOURCE_RIGHT {
			if p.rightBuf != nil {
				n, err = p.rightBuf.Write(content.Pcm)
				p.rightFrameCnt++

				//if p.rightWriter != nil {
				//	err = p.rightWriter.AppendPCM(content.Pcm)
				//	if err != nil {
				//		log.WarnK(sId, "WsPcmProcessor,Right,AppendPCM,err=%v", err)
				//	}
				//}

			}
		}

		if err != nil {
			log.WarnK(sId, "WsPcmProcessor,Write,n=%v,err=%v", n, err)
			return nil
		}

		//监控大小对不对
		if p.leftFrameCnt < 3 || p.rightFrameCnt < 3 {
			if len(content.Pcm)%32 != 0 {
				log.WarnK(sId, "WsPcmProcessor,pcm size err,pcmLen=%v", len(content.Pcm))
				//notify.HttpNotifyMedia(sId, bHeader.Notice, "pcm size err")
			}
		}

		content.Pcm = nil
		log.DebugK(sId, "WsPcmProcessor,Process,leftFrameCnt=%v,rightFrameCnt=%v", p.leftFrameCnt, p.rightFrameCnt)
		return p.writeAudio2Merge()
	}
	log.InfoK(sId, "WsPcmProcessor,WriteEvent,Event=%v", content.Event)
	if content.Event == models.EVENT_END {
		err = p.Flush(content.Event)
		if err != nil {
			log.WarnK(sId, "WsPcmProcessor,WriteEvent,Event=%v,Flush.err=%v", content.Event, err)
		}
	} else {
		//其他事件处理
	}
	content = nil
	return err
}

func (p *WsPcmProcessor) writeAudio2Merge() error {
	thresholdPass, leftAllow, rightAllow := p.threshold()
	if thresholdPass {
		var leftBufPcm, rightBufPcm []byte
		var leftIndex, rightIndex int
		var err error
		if leftAllow {
			leftBufPcm = make([]byte, p.mergeThreshold)
			leftIndex, err = p.leftBuf.Read(leftBufPcm)
			if err != nil {
				log.WarnK(p.SessionId, "WsPcmProcessor,left Buf Read,Err=%v", err)
			}
		}

		if rightAllow {
			rightBufPcm = make([]byte, p.mergeThreshold)
			rightIndex, err = p.rightBuf.Read(rightBufPcm)
			if err != nil {
				log.WarnK(p.SessionId, "WsPcmProcessor,right Buf Read,Err=%v", err)
			}
		}
		log.DebugK(p.SessionId, "WsPcm,Write,rightIndex=%v,rightIndex=%v", leftIndex, rightIndex)

		// 读出来了，无论失败还是成功，都需要去合音
		mergeChan := models.MergeChan{
			LeftPcm:  leftBufPcm,
			RightPcm: rightBufPcm,
			Event:    "",
		}
		return p.wavAc2Processor.Write(mergeChan)
	}

	return nil
}

func (p *WsPcmProcessor) threshold() (bool, bool, bool) {
	leftLen := 0
	if p.leftBuf != nil {
		leftLen = p.leftBuf.Len()
	}

	rightLen := 0
	if p.rightBuf != nil {
		rightLen = p.rightBuf.Len()
	}

	thresholdPass := false
	leftAllow := false
	rightAllow := false
	if p.source == SOURCE_BOTH {
		thresholdPass = leftLen >= p.mergeThreshold && rightLen >= p.mergeThreshold
		leftAllow = true
		rightAllow = true
	} else if p.source == SOURCE_LEFT {
		thresholdPass = leftLen >= p.mergeThreshold
		leftAllow = true
	} else if p.source == SOURCE_RIGHT {
		thresholdPass = rightLen >= p.mergeThreshold
		rightAllow = true
	}
	return thresholdPass, leftAllow, rightAllow
}

func (p *WsPcmProcessor) mergeTrigger(sample, t1, fragmentDuration int) int {
	mergeBuf := BUF_8K_TO_MERGE
	if sample != 8000 {
		mergeBuf = BUF_16K_TO_MERGE
	}

	//math.Gcd(t1, fragmentDuration)
	//这里根据时长拉长了合音
	if t1%5 == 0 && fragmentDuration%5 == 0 {
		return mergeBuf * 5
	} else if t1%2 == 0 && fragmentDuration%2 == 0 {
		return mergeBuf * 2
	}
	//mergeBuf*dur  or  mergeBuf
	return mergeBuf
}

func (p *WsPcmProcessor) Flush(event string) error {
	log.InfoK(p.SessionId, "WsPcmProcessor,leftFrameCnt=%v,rightFrameCnt=%v,Flush start", p.leftFrameCnt, p.rightFrameCnt)
	if p.wavAc2Processor == nil {
		log.InfoK(p.SessionId, "WsPcmProcessor,Flush,wavAc2Processor nil")
		return errors.New("WsPcmProcessor not init")
	}

	var leftBufPcm []byte
	var rightBufPcm []byte
	if p.leftBuf != nil {
		leftBufPcm = make([]byte, p.leftBuf.Len())
		leftN, leftErr := p.leftBuf.Read(leftBufPcm)
		log.InfoK(p.SessionId, "WsPcmProcessor,Flush,leftN=%v,leftErr=%v", leftN, leftErr)
	}

	if p.rightBuf != nil {
		rightBufPcm = make([]byte, p.rightBuf.Len())
		rightN, rightErr := p.rightBuf.Read(rightBufPcm)
		log.InfoK(p.SessionId, "WsPcmProcessor,Flush,,rightN=%v,rightErr=%v", rightN, rightErr)
	}

	//if p.leftWriter != nil {
	//	p.leftWriter.Flush()
	//	p.leftWriter.Close()
	//}

	//if p.rightWriter != nil {
	//	p.rightWriter.Flush()
	//	p.rightWriter.Close()
	//}

	eventChan := models.MergeChan{
		LeftPcm:  leftBufPcm,
		RightPcm: rightBufPcm,
		Event:    event,
	}

	var err error
	for i := 0; i < 2; i++ {
		err = p.wavAc2Processor.Write(eventChan)
		if err != nil {
			log.WarnK(p.SessionId, "WsPcmProcessor,WriteEvent,retry=%v,err=%v", i, err)
			//暂停一秒，再重试
			time.Sleep(1 * time.Second)
		} else {
			log.InfoK(p.SessionId, "WsPcmProcessor,Flush,Suc")
			break
		}
	}

	p.cleanBuffer()
	return err
}

func (p *WsPcmProcessor) Close() error {
	log.InfoK(p.SessionId, "WsPcmProcessor,Close")

	p.cleanBuffer()

	if p.wavAc2Processor != nil {
		p.wavAc2Processor.Close()
		p.wavAc2Processor = nil
	}

	return nil
}

func (p *WsPcmProcessor) cleanBuffer() {
	p.leftFrameCnt = 0
	p.rightFrameCnt = 0

	if p.leftBuf != nil {
		p.leftBuf.Reset()
		p.leftBuf = nil
	}

	if p.rightBuf != nil {
		p.rightBuf.Reset()
		p.rightBuf = nil
	}
}

func (p *WsPcmProcessor) endCallback(b bool) {
	//log.InfoK(p.SessionId, "WsPcmProcessor,EndCallback,b=%v", b)
}
