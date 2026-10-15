package wavbox

import "vi-merge/internal/pkg/log"

func Pcm2Stereo(leftBytes, rightBytes []byte, fmtType FmtType) (FmtParams, []byte, error) {
	fmtParams := GetFmtParams(fmtType)
	bs, err := mono16ToStereoDurationLong(leftBytes, rightBytes, fmtParams)
	return fmtParams, bs, err
}

func GetFmtParams(fmtType FmtType) FmtParams {
	fmtParams, ok := fmtFrameBytesMap[fmtType]
	if !ok {
		fmtParams = fmtFrameBytesMap[FmtS16le]
	}
	return fmtParams
}

func Pcm2StereoWav(leftBytes, rightBytes []byte, fmt string, sampleRate int) ([]byte, error) {
	fmtType := GetFmtType(fmt)
	fmtParams, ok := fmtFrameBytesMap[fmtType]
	if !ok {
		fmtParams = fmtFrameBytesMap[FmtS16le]
	}

	pcmAc2Dat, err := mono16ToStereoDurationLong(leftBytes, rightBytes, fmtParams)
	if err != nil {
		log.WarnF("mono16ToStereoDurationLong,err=%v", err)
		return nil, err
	}

	//下面代码是加头和数据部分
	numChannels := 2
	headWav, err := GetWavHead(len(pcmAc2Dat), fmtParams.CompressionCode, numChannels, sampleRate, fmtParams.BitsPerSample)
	if err == nil {
		return append(headWav, pcmAc2Dat...), nil
	}
	return nil, err
}

func GetFmtType(fmt string) FmtType {
	fmtType := FmtS16le
	if fmt == S16le {
		fmtType = FmtS16le
	} else if fmt == Pcma {
		fmtType = FmtPcma
	} else if fmt == Pcmu {
		fmtType = FmtPcmu
	}
	return fmtType
}

func GetWavHead(pcmLen int, compressionCode, numChannels, sampleRate, bitsPerSample int) ([]byte, error) {
	return wavHeader(pcmLen, compressionCode, numChannels, sampleRate, bitsPerSample)
}

func ReadWavHeader(wav []byte) (*WavHead, error) {
	return readWavHeader(wav)
}
