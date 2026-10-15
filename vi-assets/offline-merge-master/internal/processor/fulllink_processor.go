package processor

import (
	"fmt"
	"offline-merge/internal/audioformat"
	"offline-merge/internal/metrics"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/ff"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/processor/handler"
	"offline-merge/internal/util"
	"os"
)

const (
	EarlyConcatName = "%v_early_concat.%v"
)

type FullLinkMerge struct {
	sId          string
	transHandler handler.TransHandler
}

func NewFullLinkMerge(id string, tHandler handler.TransHandler) *FullLinkMerge {
	return &FullLinkMerge{
		sId:          id,
		transHandler: tHandler,
	}
}

func (f *FullLinkMerge) EarlyCallMerge(mergeCtx *models.MergeCtx) {
	fullLinkItem := mergeCtx.LocalOptions.FullLinkItem
	if fullLinkItem == nil {
		log.InfoK(f.sId, "fullLinkMerge,no action")
		return
	}

	earlyPairLegs := mergeCtx.LocalOptions.LocalEarlyList
	if len(earlyPairLegs) > 0 {
		earlyOutDir := util.CreateDir(TMP, MERGE_EARLY_FOLDER, "")
		emptyPath, err := handler.WriteEmpty8KMonoFile(f.sId, earlyOutDir)
		if err != nil {
			log.WarnK(f.sId, "EarlyCallMerge,write empty audio ,Path=%v", emptyPath)
			metrics.FileRwErrInc("off-empty")
		}

		audioChannels := 2
		var earlyOutLegs []string
		for _, earlyPairLeg := range earlyPairLegs {
			calleeFileLeg := earlyPairLeg.CalleeLeg
			earlyRightPath := calleeFileLeg.LocalPath
			outCode := ff.ShFail
			var mergeCfg models.MergeCfg
			if util.IsFileExist(earlyRightPath) {
				rightInCode := calleeFileLeg.InCode
				if handler.IsOriginEmpty(calleeFileLeg.InCode, calleeFileLeg.FileLength) {
					rightInCode = audioformat.UNKNOWN.GetCode()
					earlyRightPath = emptyPath
				}
				mergeCfg = handler.SetMergeCfg(mergeCtx.MergeOptions, audioChannels, audioformat.UNKNOWN.GetCode(), rightInCode)
				outCode = f.transHandler.LeftEmptyMerge(emptyPath, earlyRightPath, earlyPairLeg.ResultPath, mergeCfg)
			} else {
				mergeCfg = handler.SetMergeCfg(mergeCtx.MergeOptions, audioChannels, audioformat.UNKNOWN.GetCode(), audioformat.UNKNOWN.GetCode())
				outCode = f.transHandler.EmptyAudio(earlyPairLeg.ResultPath, mergeCfg)
			}

			var duration float32
			duration = -1
			if outCode == ff.ShSuc {
				duration = ff.DurationCal(f.sId, earlyPairLeg.ResultPath, mergeCfg.Ab)
				earlyPairLeg.ResultDuration = duration
				if duration > handler.DurationMin {
					earlyOutLegs = append(earlyOutLegs, earlyPairLeg.ResultPath)
				}
			}
		}

		if mergeCtx.MergeOptions.SeparateEarly {
			format := mergeCtx.MergeOptions.RecordSuffix
			handler.OneLegOutPath(f.sId, fullLinkItem.ResultPath, earlyOutLegs, format, func(path string) {
				duration := handler.AudioDuration(f.sId, path, audioChannels, format)
				fullLinkItem.ResultPath = path
				fullLinkItem.ResultDuration = duration
			})
		} else {
			f.fullConcat(mergeCtx, earlyOutDir, earlyOutLegs, audioChannels)
		}
		os.Remove(emptyPath)
	}
}

func (f *FullLinkMerge) fullConcat(mergeCtx *models.MergeCtx, earlyOutDir string, earlyOutLegs []string, audioChannels int) {
	format := mergeCtx.MergeOptions.RecordSuffix
	fullLinkItem := mergeCtx.LocalOptions.FullLinkItem

	earlyConcatPath := util.FilepathJoin(earlyOutDir, fmt.Sprintf(EarlyConcatName, f.sId, format)) //filepath.Join(earlyOutDir, fmt.Sprintf(EarlyConcatName, f.sId, format))
	log.InfoK(f.sId, "fullLinkMerge,earlyConcatPath=%v,earlyOutLegs=%v", earlyConcatPath, len(earlyOutLegs))

	if earlyOutLegs != nil {
		handler.OneLegOutPath(f.sId, earlyConcatPath, earlyOutLegs, format, func(path string) {
			log.InfoK(f.sId, "fullLinkMerge,earlyConcat,returnPath=%v", path)
			earlyConcatPath = path
		})
	} else {
		log.InfoK(f.sId, "fullLinkMerge,earlyOutLegs is nil")
	}

	callOutItem := mergeCtx.LocalOptions.CallOutItem

	var callDur, fullDur float32
	callDur = 0
	callOutPath := ""
	if callOutItem != nil {
		callDur = callOutItem.ResultDuration
		callOutPath = mergeCtx.LocalOptions.CallOutItem.ResultPath
	}

	earlyDur := handler.AudioDuration(f.sId, earlyConcatPath, audioChannels, format)
	log.InfoK(f.sId, "fullLinkMerge,earlyDur=%v,callDur=%v", earlyDur, callDur)
	fullOutPath := ""
	if earlyDur <= 0 && callDur <= 0 {
		//1、早期媒体和通话媒体均是空的
		fullOutPath = callOutPath
		fullDur = callDur
		os.Remove(earlyConcatPath)
	} else if earlyDur > 0 && callDur <= 0 {
		//2、存在只有早期，没有通话的情况
		fullOutPath = earlyConcatPath
		fullDur = earlyDur
	} else if earlyDur <= 0 && callDur > 0 {
		//3、存在没有早期，只有通话媒体的情况
		fullOutPath = callOutPath
		fullDur = callDur
		os.Remove(earlyConcatPath)
	} else {
		//4、早期媒体，通话媒体均存在的情况
		var concatList = []string{earlyConcatPath, callOutPath}
		fullConcatTxtPath := util.CreateFileList(f.sId, concatList)

		outLen, _ := util.FileLength(callOutPath)
		waitTime := ff.GetWaitTimeMs(outLen)
		fullCode := ff.ConcatAudio(f.sId, fullConcatTxtPath, fullLinkItem.ResultPath, waitTime, format)

		os.Remove(fullConcatTxtPath)
		if fullCode == ff.ShSuc {
			fullOutPath = fullLinkItem.ResultPath
			fullDur = handler.AudioDuration(f.sId, fullLinkItem.ResultPath, audioChannels, format)
		} else {
			fullOutPath = callOutPath
			fullDur = callDur
		}
		os.Remove(earlyConcatPath)
	}
	fullLinkItem.ResultPath = fullOutPath
	fullLinkItem.ResultDuration = fullDur
	log.InfoK(f.sId, "fullLinkMerge,fullOutPath=%v,fullDur=%v", fullLinkItem.ResultPath, fullDur)
}
