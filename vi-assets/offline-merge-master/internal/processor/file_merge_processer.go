package processor

import (
	"offline-merge/internal/metrics"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/processor/bparser"
	"offline-merge/internal/processor/handler"
	"offline-merge/internal/processor/pubmsg"
	"offline-merge/internal/util"
)

type FfMerge struct {
	sessionId string
	fileStore *FileStore

	bizParser map[string]bparser.BizParser

	//parser bparser.BizParser  // TODO:one session just needs one parser

}

func NewFfMerge(sessionID string) *FfMerge {
	fileStore := NewFileStore()
	ffMerge := &FfMerge{
		sessionId: sessionID,
		fileStore: fileStore,
		bizParser: make(map[string]bparser.BizParser),
	}
	ffMerge.registerParser(bparser.NewDccParser(sessionID))
	ffMerge.registerParser(bparser.NewViParser(sessionID))
	return ffMerge
}

func (f *FfMerge) registerParser(p bparser.BizParser) {
	bizMode := p.BizMode()
	f.bizParser[bizMode] = p
	//log.InfoF("registerParser,%s", bizMode)
}

func (f *FfMerge) GetBizParser(bizType string) bparser.BizParser {
	p, ok := f.bizParser[bizType]
	if !ok {
		//默认用dcc的解析/Vi
		//return bparser.NewDccParser(f.sessionId)
		return bparser.NewViParser(f.sessionId)
	}
	return p
}

func (f *FfMerge) MergeSh(msg string, mode string) (string, string, error) {
	mediaMerge, options, err := ParserMergeJson(f.sessionId, msg)
	if err != nil {
		log.WarnK(f.sessionId, "ParserMergeJson,err=%v", err)

		metrics.MQMessageInvalidInc(mode)
		return "", mode, models.MqMessageInvalid
	}

	if len(options.Mode) < 1 {
		options.Mode = mode
	}

	if mediaMerge.Fallback {
		metrics.FallbackInc(options.Mode)
	}

	if len(f.sessionId) < 10 {
		f.sessionId = mediaMerge.SessionID
	}

	// 业务模式
	bizParser := f.GetBizParser(options.Mode) // f.GetBizParser(models.BizVi)
	mergeCtx := f.MergeCtx(mediaMerge, options, bizParser)

	// download stage. TODO: add failed handler,such as return error
	batchFileDown := NewBatchFileDown(f.sessionId, options.Mode)
	batchFileDown.FileDown(mergeCtx)

	// ffmpeg command stage,TODO,add failed handler
	transHandler := handler.AudioHandle(f.sessionId, options.RecordSuffix)
	callMerge := NewCallMerge(f.sessionId, transHandler)
	callMerge.multiLegMerge(mergeCtx)

	fullLinkMerge := NewFullLinkMerge(f.sessionId, transHandler)
	fullLinkMerge.EarlyCallMerge(mergeCtx)

	// upload stage
	f.fileStore.Init(f.sessionId, mediaMerge.Volume, mediaMerge.Address)
	uploadSum, failCnt := f.fileStore.SaveFile(mergeCtx, bizParser)
	log.InfoK(f.sessionId, "MergeSh,SaveFile,uploadSum=%v,failCnt=%v", uploadSum, failCnt)

	cleanUp(mergeCtx)

	// send bill mq message stage
	err = pubmsg.PubMessageMQ(f.sessionId, bizParser.BizMode(), f.fileStore.FileNode(), f.fileStore.volume, mergeCtx)
	if err != nil {
		//metrics.Send2BillFailInc("offline")
		metrics.Send2BillFailInc(options.Mode)
		log.InfoK(f.sessionId, "MergeSh,PubMessageMQ,mode=%v,err=%v", bizParser.BizMode(), err)
		return mediaMerge.SessionID, options.Mode, err
	}
	return mediaMerge.SessionID, options.Mode, nil
}

func (f *FfMerge) MergeCtx(mediaMerge *models.MediaMerge, options *models.MergeOptions, parser bparser.BizParser) *models.MergeCtx {
	callParser := NewCallParser(f.sessionId, parser)
	localOptions := callParser.ParserLocalOptions(*mediaMerge, *options)
	storeDir := util.CreateDir(DATA, "", mediaMerge.Path)

	//获取Merge上下文信息
	mergeCtx := &models.MergeCtx{
		MergeOptions:  *options,
		MergeMessage:  *mediaMerge,
		LocalOptions:  localOptions,
		LocalStoreDir: storeDir,
	}
	return mergeCtx
}
