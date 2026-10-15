package processor

import (
	"offline-merge/internal/models"
	"os"
)

func cleanUp(mergeCtx *models.MergeCtx) {
	localOptions := mergeCtx.LocalOptions
	deletePairs(localOptions.LocalLegList)
	deletePairs(localOptions.LocalEarlyList)

	deleteResultItem(localOptions.CallOutItem)
	deleteResultItem(localOptions.FullLinkItem)

	deleteResultItem(localOptions.CallerItem)
	deleteResultItem(localOptions.CalleeItem)
	deleteResultItem(localOptions.VideoItem)
}

func deletePairs(pairList []*models.PairFileLeg) {
	if pairList == nil {
		return
	}
	for _, pairFileLeg := range pairList {
		deletePairFileResult(pairFileLeg)
	}
}

func deletePairFileResult(pairFileLeg *models.PairFileLeg) {
	os.Remove(pairFileLeg.ResultPath)
	//if err != nil {
	//	log.WarnF("deletePairFileResult,path=%v,err=%v", pairFileLeg.ResultPath, err)
	//}

	deleteFileLeg(pairFileLeg.CallerLeg)
	deleteFileLeg(pairFileLeg.CalleeLeg)
}

func deleteFileLeg(fileLeg *models.FileLeg) error {
	if fileLeg != nil && len(fileLeg.LocalPath) > 5 {
		return os.Remove(fileLeg.LocalPath)
	}
	return nil
}

func deleteResultItem(resultItem *models.LocalResultItem) error {
	if resultItem != nil && len(resultItem.ResultPath) > 5 {
		return os.Remove(resultItem.ResultPath)
	}
	return nil
}
