package processor

import (
	"encoding/json"
	"fmt"
	"offline-merge/internal/metrics"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/processor/bparser"
	"offline-merge/internal/util"
	"strings"
)

const (
	//通话的目录
	MERGE_FOLDER = "mergeCall"

	//通话单个文件
	MERGE_SINGLE_FOLDER = "mergeSingle"

	//早期媒体目录
	MERGE_EARLY_FOLDER = "mergeEarly"

	//全量音频目录
	MERGE_FULL_FOLDER = "mergeFull"

	EarlyFormat = "Early_%v.amr"

	TMP  = "/tmp/"
	DATA = "/data/"
)

func ParserMergeJson(sId string, jsonStr string) (*models.MediaMerge, *models.MergeOptions, error) {
	// 数据解析
	mediaMerge := &models.MediaMerge{}
	err := json.Unmarshal([]byte(jsonStr), mediaMerge)
	if err != nil {
		log.WarnK(sId, "ParserMergeJson,data=%v,Unmarshal,err=%v", jsonStr, err)
		return nil, nil, err
	}

	if len(sId) < 10 {
		sId = mediaMerge.SessionID
	}

	//log.InfoK(sId, "ParserMergeJson,UserConfig=%v", mediaMerge.UserConfig)
	if len(mediaMerge.UserConfig) > 1 {
		config := &models.Config{}
		err = json.Unmarshal([]byte(mediaMerge.UserConfig), config)
		if err != nil {
			log.WarnK(sId, "ParserMergeJson,Config,Unmarshal,err=%v", err)
		}
		mediaMerge.Config = *config
		log.InfoK(sId, "mediaMerge,Config,source=%v,userData=%v", mediaMerge.Config.Source, mediaMerge.Config.UserDate)
	}

	log.InfoK(sId, "ParserMergeJson,mediaMerge=%v", mediaMerge.ToString())
	optionStr := strings.ReplaceAll(mediaMerge.Options, "\\", "")
	log.InfoK(sId, "ParserMergeJson,optionStr=%v", optionStr)
	mergeOptions := &models.MergeOptions{}
	err = json.Unmarshal([]byte(optionStr), mergeOptions)

	// TODO: return err ,not just output warning log
	if err != nil {
		log.WarnK(sId, "ParserMergeJson,Unmarshal Options,err=%v", err)
		mergeOptions.RecordSuffix = "mp3"
		mergeOptions.IsRecord = true
		mergeOptions.ReverseLeg = false
		mergeOptions.SeparateEarly = false
		mergeOptions.SeparateLeg = false
		mergeOptions.BindId = ""
		mergeOptions.Mode = ""
	}

	//var realUserData string
	//err = json.Unmarshal(mergeOptions.UserData, &realUserData)
	//if err != nil {
	//	log.WarnK(sId, "ParserMergeJson，UserData,Unmarshal Options,err=%v", err)
	//}
	realUserData := string(mergeOptions.UserData)
	realUserData = strings.ReplaceAll(realUserData, "\\", "")
	log.InfoK(sId, "ParserMergeJson,UserDataText=%v", realUserData)
	mergeOptions.UserDataText = realUserData

	return mediaMerge, mergeOptions, nil
}

type CallParser struct {
	sId string

	subParser bparser.BizParser
}

func NewCallParser(id string, p bparser.BizParser) *CallParser {
	return &CallParser{
		sId:       id,
		subParser: p,
	}
}
func (ca *CallParser) ParserLocalOptions(mediaMerge models.MediaMerge, mergeOptions models.MergeOptions) *models.LocalOptions {
	callPairLegs := ca.ParseCallAudioPair(mediaMerge, mergeOptions)
	earlyPairLegs := ca.ParseEarlyAudioPath(mediaMerge, mergeOptions)

	callOutItem := ca.callLocalResultItem(mediaMerge, mergeOptions, MERGE_FOLDER)
	separateEarly := mergeOptions.SeparateEarly

	// 全链路
	var fullLinkItem *models.LocalResultItem
	isHadFullLink := len(earlyPairLegs) > 0
	if isHadFullLink {
		if separateEarly {
			fullLinkItem = ca.earlyLocalResultItem(mediaMerge, mergeOptions, MERGE_EARLY_FOLDER)
		} else {
			fullLinkItem = ca.callLocalResultItem(mediaMerge, mergeOptions, MERGE_FULL_FOLDER)
		}
	}

	var callerOutItem, calleeOutItem *models.LocalResultItem
	if mergeOptions.SeparateLeg {
		singleFileDir := ca.createTmpDir(MERGE_SINGLE_FOLDER, "")
		callerOutName := ca.getCallerOutName(ca.sId, mergeOptions.RecordSuffix)
		callerOutPath := util.FilepathJoin(singleFileDir, callerOutName) //filepath.Join(singleFileDir, callerOutName)
		callerOutItem = models.NewLocalResultItem()
		callerOutItem.ResultName = callerOutName
		callerOutItem.ResultPath = callerOutPath

		calleeOutName := ca.getCalleeOutName(ca.sId, mergeOptions.RecordSuffix)
		calleeOutPath := util.FilepathJoin(singleFileDir, calleeOutName) //filepath.Join(singleFileDir, calleeOutName)
		calleeOutItem = models.NewLocalResultItem()
		calleeOutItem.ResultPath = calleeOutPath
		calleeOutItem.ResultName = calleeOutName
		log.InfoK(ca.sId, "localOptions,callerOutPath=%v,calleeOutPath=%v", callerOutPath, calleeOutPath)
	}

	// 兜底
	if callOutItem == nil && fullLinkItem == nil {
		callOutItem = ca.callLocalResultItem(mediaMerge, mergeOptions, MERGE_FOLDER)
	}

	return &models.LocalOptions{
		LocalLegList:   callPairLegs,
		LocalEarlyList: earlyPairLegs,
		CallOutItem:    callOutItem,
		FullLinkItem:   fullLinkItem,
		CallerItem:     callerOutItem,
		CalleeItem:     calleeOutItem,
		OutAc:          ca.subParser.AudioChannels(mediaMerge.Config.Source),
	}
}

func (ca *CallParser) ParseCallAudioPair(mediaMerge models.MediaMerge, mergeOptions models.MergeOptions) []*models.PairFileLeg {
	var callerPairLegs, calleePairLegs []*models.FileLeg
	if mediaMerge.Config.Source == 1 {
		// caller
		callerPairLegs = ca.chooseLegList(MERGE_FOLDER, mediaMerge.CallerAudioFile, mediaMerge.GetPcmAttr())
	} else if mediaMerge.Config.Source == 2 {
		// callee
		calleePairLegs = ca.chooseLegList(MERGE_FOLDER, mediaMerge.CalleeAudioFile, mediaMerge.GetPcmAttr())
	} else {
		// both
		callerPairLegs = ca.chooseLegList(MERGE_FOLDER, mediaMerge.CallerAudioFile, mediaMerge.GetPcmAttr())
		calleePairLegs = ca.chooseLegList(MERGE_FOLDER, mediaMerge.CalleeAudioFile, mediaMerge.GetPcmAttr())
	}

	maxSize := len(calleePairLegs)
	if len(calleePairLegs) > maxSize {
		maxSize = len(calleePairLegs)
		log.WarnK(ca.sId, "calleeLegsSize(%v) != calleePairLegs(%v)", len(calleePairLegs), len(calleePairLegs))
	}

	var legAlls []*models.PairFileLeg
	for j := 0; j < maxSize; j++ {
		callerLeg := ca.getCallOneLeg(j, callerPairLegs, mediaMerge.GetPcmAttr())
		calleeLeg := ca.getCallOneLeg(j, calleePairLegs, mediaMerge.GetPcmAttr())
		pairs := models.NewPairFileLegWithArgs(callerLeg, calleeLeg)

		startTime := ca.getStartTime(fmt.Sprintf("%d", mediaMerge.StartTime))
		if len(startTime) < 1 {
			// report
			//metrics.StartTimeWrongInc("offline")

			metrics.StartTimeWrongInc(mergeOptions.Mode)

		}
		callRetName := ca.subParser.CallOutName(mediaMerge.SessionID, mediaMerge.CallerNum,
			mediaMerge.CalleeNum, startTime, mergeOptions.RecordSuffix,
		)

		pairs.ResultName = callRetName
		fileDir := ca.createTmpDir(MERGE_FOLDER+"_"+fmt.Sprintf("%v", j), "")
		callRetPath := util.FilepathJoin(fileDir, callRetName) // filepath.Join(fileDir, callRetName)
		pairs.ResultPath = callRetPath

		log.InfoK(ca.sId, "ParserCallAudioPair,index=%v,callRetPath=%v", j, callRetPath)
		legAlls = append(legAlls, pairs)
	}

	return legAlls
}

func (ca *CallParser) getCallOneLeg(j int, callerPairLegs []*models.FileLeg, pcmAttr models.PcmAttr) *models.FileLeg {
	var callLeg *models.FileLeg
	if j < len(callerPairLegs) {
		callLeg = callerPairLegs[j]
	} else {
		callLeg = models.NewFileLegWithArgs("", "", pcmAttr)
	}
	return callLeg
}

func (ca *CallParser) ParseEarlyAudioPath(mediaMerge models.MediaMerge, mergeOptions models.MergeOptions) []*models.PairFileLeg {
	if !ca.subParser.FullMerge() {
		log.InfoK(ca.sId, "CallParser,ParseEarly,No FullMerge")
		return []*models.PairFileLeg{}
	}

	earlyPath := mediaMerge.EarlyAudioFile
	if earlyPath == "" {
		if !mergeOptions.SeparateEarly {
			return nil
		}
		earlyPath = fmt.Sprintf(EarlyFormat, ca.sId)
	}
	earlyList := ca.chooseLegList(MERGE_EARLY_FOLDER, earlyPath, mediaMerge.GetPcmAttr())

	var earlyPairLegs []*models.PairFileLeg
	if len(earlyList) > 0 {
		for i := 0; i < len(earlyList); i++ {
			pairs := models.NewPairFileLegWithArgs(nil, earlyList[i])
			startTimeStr := fmt.Sprintf("%v", mediaMerge.StartTime)
			earlyRetName := ca.getEarlyResultName(ca.sId, startTimeStr, mergeOptions.RecordSuffix)

			earlyResultDir := ca.createTmpDir(MERGE_EARLY_FOLDER+"_"+fmt.Sprintf("%v", i), "")
			earlyResultPath := util.FilepathJoin(earlyResultDir, earlyRetName) //filepath.Join(earlyResultDir, earlyRetName)

			log.InfoK(ca.sId, "CallParser,earlyAudioPath,earlyResultPath="+earlyResultPath)
			pairs.ResultName = earlyRetName
			pairs.ResultPath = earlyResultPath
			earlyPairLegs = append(earlyPairLegs, pairs)
		}
	}
	return earlyPairLegs
}

func (ca *CallParser) callLocalResultItem(mediaMerge models.MediaMerge, mergeOptions models.MergeOptions, dir string) *models.LocalResultItem {
	startTime := ca.getStartTime(fmt.Sprintf("%v", mediaMerge.StartTime))
	resultName := ca.subParser.CallOutName(mediaMerge.SessionID, mediaMerge.CallerNum, mediaMerge.CalleeNum,
		startTime, mergeOptions.RecordSuffix,
	)
	basePath := ca.createTmpDir(dir, "")
	callRetPath := util.FilepathJoin(basePath, resultName) //filepath.Join(basePath, resultName)
	item := models.NewLocalResultItem()
	item.ResultName = resultName
	item.ResultPath = callRetPath
	return item
}

func (ca *CallParser) earlyLocalResultItem(merge models.MediaMerge, options models.MergeOptions, folder string) *models.LocalResultItem {
	resultName := ca.getEarlyResultName(merge.SessionID, fmt.Sprintf("%d", merge.StartTime), options.RecordSuffix)
	basePath := ca.createTmpDir(folder, "")
	callRetPath := util.FilepathJoin(basePath, resultName) //filepath.Join(basePath, resultName)
	item := models.NewLocalResultItem()
	item.ResultName = resultName
	item.ResultPath = callRetPath
	return item
}

func (ca *CallParser) getEarlyResultName(sId string, sTime string, suffix string) string {
	startTime := ca.getStartTime(sTime)
	return fmt.Sprintf("Early_Record_%v_%v.%v", sId, startTime, suffix)
}

func (ca *CallParser) getCallerOutName(sId, suffix string) string {
	return fmt.Sprintf("Left_Record_%v_%v.%v", sId, suffix, suffix)
}

func (ca *CallParser) getCalleeOutName(sId, suffix string) string {
	return fmt.Sprintf("Right_Record_%v_%v.%v", sId, suffix, suffix)
}

func (ca *CallParser) getStartTime(startTimeStr string) string {
	audioTime := ""
	length := len(startTimeStr)
	switch {
	case length == 14:
		// yyyyMMddHHmmss
		log.InfoK(ca.sId, "may be error1=%v", startTimeStr)
		audioTime = startTimeStr
	case length == 13:
		audioTime = util.Timestamp13StrToYmdHis(startTimeStr)
	case length == 10:
		audioTime = util.TimestampStr10ToYmdHis(startTimeStr)
	default:
		log.WarnK(ca.sId, "startTime=%v may be error2")
	}
	return audioTime
}

func (ca *CallParser) chooseLegList(subBaseDir, name string, pcmAttr models.PcmAttr) []*models.FileLeg {
	legs := strings.Split(name, ",")
	var list []*models.FileLeg
	for i, leg := range legs {
		leg = strings.TrimSpace(leg)
		if leg == "" {
			continue
		}
		fileDir := ca.createTmpDir(fmt.Sprintf("%v_%v", subBaseDir, i), "")
		log.InfoK(ca.sId, "call,chooseLegList,fileDir=%v,name=%v", fileDir, leg)
		if len(leg) > 18 {
			filePath := util.FilepathJoin(fileDir, leg) // filepath.Join(fileDir, leg)
			fileLeg := models.NewFileLegWithArgs(leg, filePath, pcmAttr)
			list = append(list, fileLeg)
		}
	}
	return list
}

func (ca *CallParser) createTmpDir(subDir, path string) string {
	return util.CreateDir(TMP, subDir, path)
}
