package handler

import (
	"offline-merge/internal/audioformat"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/ff"
	"offline-merge/internal/pkg/log"
	"os"
)

type Mp3Transcoder struct {
	sId          string
	resultSuffix string
}

func NewMp3Transcoder(id string) TransHandler {
	return &Mp3Transcoder{
		sId:          id,
		resultSuffix: MP3,
	}
}

func (m *Mp3Transcoder) EmptyAudio(outPath string, params models.MergeCfg) int {
	ar := params.Ar
	if ar == 8000 {
		os.WriteFile(outPath, audioformat.MP3_8K_EMPTY, 0644)
	} else {
		os.WriteFile(outPath, audioformat.MP3_16K_EMPTY, 0644)
	}
	return ff.ShSuc
}

func (m *Mp3Transcoder) MergeAudio(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(m.sId, "Mp3Transcoder,mergeAudio,left=%v,acReverse=%v", left, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(m.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, m.resultSuffix)
	}
	if acReverse {
		return ff.Merge2Stereo(m.sId, right, left, outPath, params.Ar, params.Ab, ff.Libmp3lame, params.RightInCode, params.LeftInCode, m.resultSuffix)
	}
	return ff.Merge2Stereo(m.sId, left, right, outPath, params.Ar, params.Ab, ff.Libmp3lame, params.LeftInCode, params.RightInCode, m.resultSuffix)
}

func (m *Mp3Transcoder) SingleAudioFile(path, outPath string, inCode int, cfg models.MergeCfg) int {
	log.InfoK(m.sId, "AudioMp3Handle,SingleAudioFile=%v", path)
	return ff.Audio2Mono(m.sId, path, outPath, cfg.Ar, cfg.Ab, cfg.Ac, inCode, m.resultSuffix)
}

func (m *Mp3Transcoder) LeftPadMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(m.sId, "AudioMp3Handle,leftPadMerge,left=%v,acReverse=%v", left, acReverse)

	if params.IsAcOne() {
		return ff.Mix2MonoLong(m.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, m.resultSuffix)
	}
	if acReverse {
		return ff.RightShortMerge(m.sId, right, left, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.RightInCode, params.LeftInCode)
	}
	return ff.LeftShortMerge(m.sId, left, right, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.LeftInCode, params.RightInCode)
}

func (m *Mp3Transcoder) RightPadMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(m.sId, "AudioMp3Handle,rightPadMerge,left=%v,acReverse=%v", left, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(m.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, m.resultSuffix)
	}
	if acReverse {
		return ff.LeftShortMerge(m.sId, right, left, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.RightInCode, params.LeftInCode)
	}
	return ff.RightShortMerge(m.sId, left, right, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.LeftInCode, params.RightInCode)
}

func (m *Mp3Transcoder) LeftEmptyMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(m.sId, "AudioMp3Handle,leftEmptyMerge,right=%v,acReverse=%v", right, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(m.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, m.resultSuffix)
	}
	if acReverse {
		return ff.RightEmptyMerge(m.sId, right, left, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.RightInCode, params.LeftInCode)
	}
	return ff.LeftEmptyMerge(m.sId, left, right, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.LeftInCode, params.RightInCode)
}

func (m *Mp3Transcoder) RightEmptyMerge(left, right, outPath string, params models.MergeCfg) int {
	acReverse := params.AcReverse
	log.InfoK(m.sId, "AudioMp3Handle,leftEmptyMerge,left=%v,acReverse=%v", left, acReverse)
	if params.IsAcOne() {
		return ff.Mix2MonoLong(m.sId, left, right, outPath, params.Ar, params.Ab, 1, params.LeftInCode, params.RightInCode, m.resultSuffix)
	}
	if acReverse {
		return ff.LeftEmptyMerge(m.sId, right, left, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.RightInCode, params.LeftInCode)
	}
	return ff.RightEmptyMerge(m.sId, left, right, outPath, m.resultSuffix, params.Ar, params.Ab, ff.Libmp3lame, params.LeftInCode, params.RightInCode)
}
