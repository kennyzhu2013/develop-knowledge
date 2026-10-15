package pubmsg

import (
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/broker/pub"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/util"
)

func PubMessageMQ(sId string, mode string, node string, volume string, mergeCtx *models.MergeCtx) error {
	mediaMerge := mergeCtx.MergeMessage
	options := mergeCtx.MergeOptions
	callDuration, fullDuration, earlyDuration := getDur(mergeCtx, options)
	retName := mergeCtx.LocalOptions.CallOutItem.ResultName

	var billMsg interface{}
	billInfo := ""
	ProducerInterface := pub.GetProducer(mode)
	if mode == models.BizVi {
		viBillMessage := getViBillMsg(sId, mediaMerge, callDuration, retName, node, volume, options)
		billInfo = viBillMessage.String()
		billMsg = viBillMessage
	} else {
		dccBillMessage := getDccBillMsg(mergeCtx, retName, callDuration, fullDuration, earlyDuration)
		billInfo = dccBillMessage.String()
		billMsg = dccBillMessage
	}

	if err := ProducerInterface.PubBillMessage(sId, billMsg); err != nil {
		log.WarnK(sId, "Publish PubBillMessage failed, mode=%v,message: %v, cause: %v", mode, billInfo, err)
		return err
	}
	//log.InfoK(sId, "Publish PubBillMessage success, mode=%v,message: %v", mode, billInfo)
	return nil
}
func getDccBillMsg(mergeCtx *models.MergeCtx, retName string, callDuration float32, fullDuration float32,
	earlyDuration float32) *pub.DccBillMessage {
	mediaMerge := mergeCtx.MergeMessage
	options := mergeCtx.MergeOptions
	leftFile, rightFile, leftLen, rightLen, codec := getResultPairLeg(mergeCtx)
	outLen := getOutLen(mergeCtx)
	fullRecord := mergeCtx.LocalOptions.FullLinkItem != nil
	dccBillMessage := &pub.DccBillMessage{
		Code:                 pub.TypeSuc,
		FileName:             retName,
		LeftFile:             leftFile,
		RightFile:            rightFile,
		LeftFileLength:       leftLen,
		RightFileLength:      rightLen,
		CombineFileLength:    int(outLen),
		UserId:               mediaMerge.UserID,
		ServerId:             mediaMerge.Address,
		FullDuration:         util.MathCeil(callDuration),
		FileDuration:         util.MathCeil(fullDuration),
		EarlyDuration:        util.MathCeil(earlyDuration),
		Acodec:               codec,
		SeparateEarly:        options.SeparateEarly,
		FullRecord:           fullRecord,
		BindId:               options.BindId,
		LeftRtpCounter:       mediaMerge.CallerAudioRtpCount,
		RightRtpCounter:      mediaMerge.CalleeAudioRtpCount,
		LeftRtpWriteCounter:  mediaMerge.CallerAudioRecordCount,
		RightRtpWriteCounter: mediaMerge.CalleeAudioRecordCount,
	}
	return dccBillMessage
}

func getViBillMsg(sId string, mediaMerge models.MediaMerge, callDuration float32,
	retName string, fileNode, volume string, options models.MergeOptions) *pub.ViBillMessage {
	end := true
	log.InfoK(sId, "PubMessageMQ start,fileNode=%v,Config.UserDate=%v,callDuration=%v", fileNode, mediaMerge.Config.UserDate, callDuration)
	userDate := mediaMerge.Config.UserDate
	if len(userDate) < 1 {
		userDate = options.UserDataText
	}
	viBillMessage := &pub.ViBillMessage{
		SessionId: sId,
		SegmentID: 0,
		CallerNum: mediaMerge.CallerNum,
		CalleeNum: mediaMerge.CalleeNum,
		UserId:    mediaMerge.UserID,
		FileNode:  fileNode,
		EndFlag:   end,
		IsStream:  false,
		Duration:  callDuration,
		Path:      mediaMerge.Path,
		Volume:    volume,
		FileName:  retName,
		Suffix:    options.RecordSuffix,
		UserData:  userDate,
		Fallback:  mediaMerge.Fallback,
	}
	return viBillMessage
}

func getOutLen(mergeCtx *models.MergeCtx) int64 {
	var outLen int64 = 0
	callOutItem := mergeCtx.LocalOptions.CallOutItem
	if callOutItem != nil {
		outLen = callOutItem.FileLen
	}
	return outLen
}

func getResultPairLeg(mergeCtx *models.MergeCtx) (string, string, int64, int64, int) {
	leftFile, rightFile := "", ""
	var leftLen, rightLen int64 = 0, 0
	var codec int
	pairFileLegs := mergeCtx.LocalOptions.LocalLegList
	if len(pairFileLegs) > 0 {
		callerLeg := pairFileLegs[0].CallerLeg
		if callerLeg != nil {
			leftFile = callerLeg.FileName
			leftLen = callerLeg.FileLength

			codec = callerLeg.InCode
		}

		calleeLeg := pairFileLegs[0].CalleeLeg
		if calleeLeg != nil {
			rightFile = calleeLeg.FileName
			rightLen = calleeLeg.FileLength
			if codec < 0 {
				codec = calleeLeg.InCode
			}
		}
	}
	return leftFile, rightFile, leftLen, rightLen, codec
}

func getDur(mergeCtx *models.MergeCtx, options models.MergeOptions) (float32, float32, float32) {
	var callDuration, fullDuration, earlyDuration float32

	callOutItem := mergeCtx.LocalOptions.CallOutItem
	if callOutItem != nil {
		callDuration = callOutItem.ResultDuration
	}

	fullLinkItem := mergeCtx.LocalOptions.FullLinkItem
	if fullLinkItem != nil {
		if options.SeparateEarly {
			earlyDuration = fullLinkItem.ResultDuration
			fullDuration = fullLinkItem.ResultDuration + callDuration
		} else {
			fullDuration = fullLinkItem.ResultDuration
		}
	}
	return callDuration, fullDuration, earlyDuration
}
