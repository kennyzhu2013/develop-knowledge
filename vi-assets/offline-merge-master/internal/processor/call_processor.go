package processor

import (
	"offline-merge/internal/audioformat"
	"offline-merge/internal/metrics"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/ff"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/processor/handler"
	"offline-merge/internal/util"
)

type CallMerge struct {
	sId          string
	transHandler handler.TransHandler
}

func NewCallMerge(id string, tHandler handler.TransHandler) *CallMerge {
	return &CallMerge{
		sId:          id,
		transHandler: tHandler,
	}
}

// TODO: add return
func (ca *CallMerge) multiLegMerge(mergeCtx *models.MergeCtx) {
	var singleCallerOutLegs []string
	var singleCalleeOutLegs []string

	callLegs := mergeCtx.LocalOptions.LocalLegList
	for _, callPairLeg := range callLegs {
		callerLeg := callPairLeg.CallerLeg
		calleeLeg := callPairLeg.CalleeLeg

		callerInjured := callerLeg.FileLength <= 100
		calleeInjured := calleeLeg.FileLength <= 100

		callResultPath := callPairLeg.ResultPath
		mergeCfg := handler.SetMergeCfg(mergeCtx.MergeOptions, mergeCtx.LocalOptions.OutAc, callerLeg.InCode, calleeLeg.InCode)
		log.InfoK(ca.sId, "call,merge,callerInjured=%v,calleeInjured=%v,callResultPath=%v,outAc=%v",
			callerInjured, calleeInjured, callResultPath, mergeCtx.LocalOptions.OutAc)
		outCode := -1
		if callerInjured && calleeInjured {
			// 两腿都伤，合成空录音
			log.InfoK(ca.sId, "call,merge,EmptyAudio,callResultPath=%v", callResultPath)
			outCode = ca.transHandler.EmptyAudio(callResultPath, mergeCfg)
		} else if callerInjured && !calleeInjured {
			// 被叫正常，单腿合并
			log.InfoK(ca.sId, "call,merge,OneLegMerge,right,callResultPath=%v", callResultPath)
			outCode = handler.OneLegMerge(ca.sId, ca.transHandler, false, calleeLeg.LocalPath, callResultPath, mergeCfg)
		} else if !callerInjured && calleeInjured {
			// 主叫正常，单腿合并
			log.InfoK(ca.sId, "call,merge,OneLegMerge left,callResultPath=%v", callResultPath)
			outCode = handler.OneLegMerge(ca.sId, ca.transHandler, true, callerLeg.LocalPath, callResultPath, mergeCfg)
		} else {
			// 两腿正常，双腿merge
			diffTime := callerLeg.Duration - calleeLeg.Duration
			if diffTime > 0 {
				// 被叫短合并
				log.InfoK(ca.sId, "call,merge,RightPadMerge,callResultPath=%v", callResultPath)
				outCode = ca.transHandler.RightPadMerge(callerLeg.LocalPath, calleeLeg.LocalPath, callResultPath, mergeCfg)
				metrics.MergeLegDiffTimeObserve(mergeCtx.Mode(), float64(diffTime))
			} else { //
				log.InfoK(ca.sId, "call,merge,LeftPadMerge,callResultPath=%v", callResultPath)
				outCode = ca.transHandler.LeftPadMerge(callerLeg.LocalPath, calleeLeg.LocalPath, callResultPath, mergeCfg)
			}
		}

		var duration float32
		if outCode == ff.ShSuc {
			duration = ff.DurationCal(ca.sId, callResultPath, mergeCfg.Ab)
			callPairLeg.ResultDuration = duration
		} else {
			// 记录，后续想一个兜底策略
			log.InfoK(ca.sId, "call,merge fail=%v", callResultPath)
			metrics.FFCmdFailInc(mergeCtx.Mode(), ff.GetStringCode(outCode))
		}

		log.InfoK(ca.sId, "multiLegMerge,callResultPath=%v,outCode=%v,duration=%v", callResultPath, outCode, duration)
		if mergeCtx.MergeOptions.SeparateLeg {
			recordSuffix := mergeCtx.MergeOptions.RecordSuffix
			callerLocalOutPath := ca.localOutPath(callerLeg, recordSuffix, mergeCfg)
			if len(callerLocalOutPath) > 0 {
				singleCallerOutLegs = append(singleCallerOutLegs, callerLocalOutPath)
			}
			calleeLocalOutPath := ca.localOutPath(calleeLeg, recordSuffix, mergeCfg)
			if len(calleeLocalOutPath) > 0 {
				singleCalleeOutLegs = append(singleCalleeOutLegs, calleeLocalOutPath)
			}
		}
	}

	if len(callLegs) > 0 {
		ca.multiCallResult(callLegs, mergeCtx.LocalOptions.CallOutItem, mergeCtx.MergeOptions, func(path string, dur float32) {
			mergeCtx.LocalOptions.CallOutItem.ResultDuration = dur
			mergeCtx.LocalOptions.CallOutItem.ResultPath = path
			mergeCtx.LocalOptions.CallOutItem.FileLen, _ = util.FileLength(path)
		})
	} else if mergeCtx.LocalOptions.CallOutItem != nil {
		mergeCfg := handler.SetMergeCfg(mergeCtx.MergeOptions, 1, audioformat.UNKNOWN.GetCode(), audioformat.UNKNOWN.GetCode())
		callResultPath := mergeCtx.LocalOptions.CallOutItem.ResultPath
		outCode := ca.transHandler.EmptyAudio(callResultPath, mergeCfg)
		log.InfoK(ca.sId, "call,merge,emptyAudio,callResultPath=%v,,outCode=%v", callResultPath, outCode)
	}

	// caller merge out file
	callerItem := mergeCtx.LocalOptions.CallerItem
	if callerItem != nil {
		handler.OneLegOutPath(ca.sId, callerItem.ResultPath, singleCallerOutLegs, mergeCtx.MergeOptions.RecordSuffix, func(path string) {
			mergeCtx.LocalOptions.CallerItem.ResultPath = path
		})
	}

	// callee merge out file
	calleeItem := mergeCtx.LocalOptions.CalleeItem
	if calleeItem != nil {
		handler.OneLegOutPath(ca.sId, calleeItem.ResultPath, singleCalleeOutLegs, mergeCtx.MergeOptions.RecordSuffix, func(path string) {
			mergeCtx.LocalOptions.CalleeItem.ResultPath = path
		})
	}
}

func (ca *CallMerge) multiCallResult(callPairLegs []*models.PairFileLeg, callOutItem *models.LocalResultItem, mergeOptions models.MergeOptions, callback func(path string, dur float32)) {
	log.InfoK(ca.sId, "call,merge,multiCallResult,callResultPath=%v,size=%v", callOutItem.ResultPath, len(callPairLegs))
	if len(callPairLegs) > 1 {
		//开始多段合并
		var duration float32
		duration = 0
		var concatAudios []string
		for _, callPairLeg := range callPairLegs {
			if callPairLeg.ResultDuration > handler.DurationMin {
				concatAudios = append(concatAudios, callPairLeg.ResultPath)
				duration += callPairLeg.ResultDuration
			}
		}
		concatAudiosLen := len(concatAudios)
		if concatAudiosLen > 1 {
			outOk := handler.ConcatAudios(ca.sId, concatAudios, callOutItem.ResultPath, mergeOptions.RecordSuffix)
			if !outOk {
				log.WarnK(ca.sId, "multiCall,multi legs merge fail,fileLen=%v,callResultPath=%v", concatAudiosLen, concatAudios[0])
				callback(callPairLegs[0].ResultPath, callPairLegs[0].ResultDuration)
			} else {
				log.InfoK(ca.sId, "multiCall,multi legs merge Suc,fileLen=%v,callResultPath=%v,duration=%v", concatAudiosLen, callOutItem.ResultPath, duration)
				callback(callOutItem.ResultPath, duration)
			}
		} else if concatAudiosLen == 1 {
			log.InfoK(ca.sId, "multiCall,multi legs merge fail2,fileLen=%v,callResultPath=%v", concatAudiosLen, concatAudios[0])
			callback(callPairLegs[0].ResultPath, callPairLegs[0].ResultDuration)
		} else {
			mergeCfg := handler.SetMergeCfg(mergeOptions, 1, audioformat.UNKNOWN.GetCode(), audioformat.UNKNOWN.GetCode())
			ca.transHandler.EmptyAudio(callOutItem.ResultPath, mergeCfg)
			log.InfoK(ca.sId, "multiCall,multi legs=0,EmptyAudio,firstPath=%v,", callOutItem.ResultPath)
			callback(callOutItem.ResultPath, 0)
		}
	} else if len(callPairLegs) == 1 {
		log.InfoK(ca.sId, "multiCall,callPairLegs=1,callResultPath="+callPairLegs[0].ResultPath)
		callback(callPairLegs[0].ResultPath, callPairLegs[0].ResultDuration)
	}
}

func (ca *CallMerge) localOutPath(callerLeg *models.FileLeg, outFormat string, mergeCfg models.MergeCfg) string {
	callLocalPath := callerLeg.LocalPath
	callLocalOutPath := util.ChangeExt(callLocalPath, outFormat)
	//reset ac
	mergeCfg.Ac = 1
	outCode := ca.transHandler.SingleAudioFile(callLocalPath, callLocalOutPath, callerLeg.InCode, mergeCfg)
	if outCode == ff.ShSuc {
		return callLocalOutPath
	}
	return ""
}
