package handler

import (
	"offline-merge/internal/audioformat"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/ff"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/util"
	"os"
	"path/filepath"
)

const (
	//pcm编码，8k，单声道s16le:128kb/s ; pcm_alaw:64kb/s; pcm_u8:64kb/s
	WAV_PCM_S16LE = "pcm_s16le"
	WAV_PCM_ALAW  = "pcm_alaw"

	WAV_PCM_ULAW = "pcm_mulaw"
	WAV_PCM_U8   = "pcm_u8"

	DefaultSample = 8000

	DurationMin = 0.1
)

type TransHandler interface {
	EmptyAudio(outPath string, params models.MergeCfg) int

	MergeAudio(left, right, outPath string, params models.MergeCfg) int

	SingleAudioFile(path, outPath string, inCode int, params models.MergeCfg) int

	LeftPadMerge(left, right, outPath string, params models.MergeCfg) int

	RightPadMerge(left, right, outPath string, params models.MergeCfg) int

	LeftEmptyMerge(left, right, outPath string, params models.MergeCfg) int

	RightEmptyMerge(left, right, outPath string, params models.MergeCfg) int
}

const (
	MP3 = "mp3"
	WAV = "wav"

	DefaultPcmFmt = WAV_PCM_S16LE
)

func AudioHandle(sid, suffix string) TransHandler {
	if suffix == WAV {
		return NewWavTranscoder(sid)
	}
	return NewMp3Transcoder(sid)
}

func OneLegMerge(sId string, transHandler TransHandler, isLeftHealth bool, path, outPath string, params models.MergeCfg) int {
	var mergeResult int
	log.InfoK(sId, "OneLegMerge,path=%v,outPath=%v,ac=%v", path, outPath, params.Ac)
	if params.IsAcOne() {
		mergeResult = transHandler.SingleAudioFile(path, outPath, params.LeftInCode, params)
		if mergeResult == ff.ShFail {
			log.WarnK(sId, "oneAudioMerge,fail,CreateEmpty,path=%v,outPath=%v", path, outPath)
			mergeResult = transHandler.EmptyAudio(outPath, params)
		}
	} else {
		dirPath := filepath.Dir(outPath)
		emptyPath, err := WriteEmpty8KMonoFile(sId, dirPath)
		if err != nil {
			log.WarnK(sId, "oneAudioMerge,path=%v,emptyPath=%v,outPath=%v", path, emptyPath, outPath)
		}
		if isLeftHealth {
			mergeResult = transHandler.RightEmptyMerge(path, emptyPath, outPath, params)
		} else {
			mergeResult = transHandler.LeftEmptyMerge(emptyPath, path, outPath, params)
		}

		os.Remove(emptyPath)
	}
	return mergeResult
}

func WriteEmpty8KMonoFile(sId string, dirPath string) (string, error) {
	emptyPath := dirPath + "/" + sId + "_empty_8k.mp3"
	err := os.WriteFile(emptyPath, audioformat.MP3_8K_EMPTY, 0644)
	return emptyPath, err
}

func ConcatAudios(sId string, audioList []string, concatOutPath, outFormat string) bool {
	size := len(audioList)
	concatFilePath := util.CreateFileList(sId, audioList)
	//目录不存在，则创建目录

	folderPath := filepath.Dir(concatOutPath)
	err := os.MkdirAll(folderPath, 0755)
	if err != nil {
		log.WarnK(sId, "ConcatAudios,MkdirAll,err=%v", err)
	}
	waitTime := size * 2 * 1000
	fullCode := ff.ConcatAudio(sId, concatFilePath, concatOutPath, waitTime, outFormat)
	os.Remove(concatFilePath)
	if fullCode == ff.ShSuc {
		return true
	}
	return false
}

func OneLegOutPath(sId, resultPath string, singleOutLegs []string, outFormat string, concatCallback func(path string)) {
	size := len(singleOutLegs)
	if size == 0 {
		//TODO:是否需要空文件
		return
	}
	if size > 1 {
		outOk := ConcatAudios(sId, singleOutLegs, resultPath, outFormat)
		log.InfoK(sId, "OneLegOutPath,outOk=%v,callResultPath=%v", outOk, resultPath)
		if !outOk {
			concatCallback(singleOutLegs[0])
		} else {
			//不回调也行，本来就是这值
			concatCallback(resultPath)
		}
	} else if size == 1 {
		log.InfoK(sId, "OneLegOutPath,one leg,callResultPath="+singleOutLegs[0])
		concatCallback(singleOutLegs[0])
	}
}

func IsOriginEmpty(inCode int, earlyRightSize int64) bool {
	if inCode == audioformat.AMR_NB.GetCode() && earlyRightSize <= (6+32) {
		return true
	}
	if inCode == audioformat.WAV_PCMA.GetCode() && earlyRightSize <= (58+160) {
		return true
	}
	if inCode == audioformat.AMR_WB.GetCode() && earlyRightSize <= (9+61) {
		return true
	}

	if inCode == audioformat.WAV_PCMU.GetCode() && earlyRightSize <= (58+160) {
		return true
	}

	if inCode == audioformat.G729.GetCode() && earlyRightSize <= 20 {
		return true
	}

	return false
}

func SetMergeCfg(mergeOptions models.MergeOptions, audioChannels, lInCode, rInCode int) models.MergeCfg {
	ab := audioAb(mergeOptions.RecordSuffix, DefaultPcmFmt, audioChannels)
	mergeCfg := models.MergeCfg{
		Ar:          DefaultSample,
		AcReverse:   mergeOptions.ReverseLeg,
		Ab:          ab,
		Codec:       DefaultPcmFmt,
		LeftInCode:  lInCode,
		RightInCode: rInCode,
		Ac:          audioChannels,
	}
	return mergeCfg
}

func AudioDuration(sId string, path string, audioChannels int, format string) float32 {
	ab := audioAb(format, DefaultPcmFmt, audioChannels)
	return ff.DurationCal(sId, path, ab)
}

func audioAb(outFormat string, pcm string, ac int) int {
	var ab int
	if MP3 == outFormat {
		ab = 16000 * ac
	} else if WAV == outFormat {
		if pcm == WAV_PCM_S16LE {
			ab = 128000 * ac
		} else {
			ab = 64000 * ac
		}
	}
	return ab
}
