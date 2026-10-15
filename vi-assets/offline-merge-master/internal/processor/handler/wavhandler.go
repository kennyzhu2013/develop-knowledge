package handler

import (
	"offline-merge/internal/audioformat"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/ff"
	"offline-merge/internal/pkg/log"
	"os"
)

type WavTranscoder struct {
	sId          string
	resultSuffix string
}

func NewWavTranscoder(id string) TransHandler {
	return &WavTranscoder{
		sId:          id,
		resultSuffix: WAV,
	}
}

func (w *WavTranscoder) EmptyAudio(outPath string, params models.MergeCfg) int {
	ar := params.Ar
	if ar == 8000 {
		os.WriteFile(outPath, audioformat.S16LE_8K_EMPTY, 0644)
	} else {
		os.WriteFile(outPath, audioformat.S16LE_16K_EMPTY, 0644)
	}
	return ff.ShSuc
}

func (w *WavTranscoder) MergeAudio(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(w.sId, "WavTranscoder,mergeAudio,left=%v,acReverse=%v", left, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(w.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, w.resultSuffix)
	}
	if acReverse {
		return ff.Merge2Stereo(w.sId, right, left, outPath, params.Ar, params.Ab, WAV_PCM_S16LE, params.RightInCode, params.LeftInCode, w.resultSuffix)
	}
	return ff.Merge2Stereo(w.sId, left, right, outPath, params.Ar, params.Ab, WAV_PCM_S16LE, params.LeftInCode, params.RightInCode, w.resultSuffix)
}

func (w *WavTranscoder) SingleAudioFile(path, outPath string, inCode int, cfg models.MergeCfg) int {
	log.InfoK(w.sId, "WavTranscoder,SingleAudioFile=%v", path)
	return ff.Audio2Mono(w.sId, path, outPath, cfg.Ar, cfg.Ab, cfg.Ac, inCode, w.resultSuffix)
}

func (w *WavTranscoder) LeftPadMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(w.sId, "WavTranscoder,leftPadMerge,,acReverse=%v", acReverse)

	if params.IsAcOne() {
		return ff.Mix2MonoLong(w.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, w.resultSuffix)
	}
	if acReverse {
		return ff.RightShortMerge(w.sId, right, left, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.RightInCode, params.LeftInCode)
	}
	return ff.LeftShortMerge(w.sId, left, right, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.LeftInCode, params.RightInCode)
}

func (w *WavTranscoder) RightPadMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(w.sId, "WavTranscoder,rightPadMerge,left=%v,acReverse=%v", left, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(w.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, w.resultSuffix)
	}
	if acReverse {
		return ff.LeftShortMerge(w.sId, right, left, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.RightInCode, params.LeftInCode)
	}
	return ff.RightShortMerge(w.sId, left, right, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.LeftInCode, params.RightInCode)
}

func (w *WavTranscoder) LeftEmptyMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(w.sId, "WavTranscoder,leftEmptyMerge,right=%v,acReverse=%v", right, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(w.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, w.resultSuffix)
	}
	if acReverse {
		return ff.RightEmptyMerge(w.sId, right, left, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.RightInCode, params.LeftInCode)
	}
	return ff.LeftEmptyMerge(w.sId, left, right, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.LeftInCode, params.RightInCode)
}

func (w *WavTranscoder) RightEmptyMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(w.sId, "WavTranscoder,leftEmptyMerge,left=%v,acReverse=%v", left, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(w.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, w.resultSuffix)
	}
	if acReverse {
		return ff.LeftEmptyMerge(w.sId, right, left, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.RightInCode, params.LeftInCode)
	}
	return ff.RightEmptyMerge(w.sId, left, right, outPath, w.resultSuffix, params.Ar, params.Ab, WAV_PCM_S16LE, params.LeftInCode, params.RightInCode)
}
