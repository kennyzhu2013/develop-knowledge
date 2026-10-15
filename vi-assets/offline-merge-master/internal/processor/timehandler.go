package processor

import (
	"fmt"
	"offline-merge/internal/audioformat"
	"offline-merge/internal/pkg/parser"
)

type CodecInfo struct {
	headSize int
	bitRate  int
}

var (
	codeTable = map[int]CodecInfo{
		audioformat.AMR_WB.GetCode():   {headSize: 9, bitRate: 23850},
		audioformat.AMR_NB.GetCode():   {headSize: 6, bitRate: 12200},
		audioformat.WAV_PCMA.GetCode(): {headSize: 58, bitRate: 64000},
		audioformat.WAV_PCMU.GetCode(): {headSize: 58, bitRate: 64000},
		audioformat.G729.GetCode():     {headSize: 0, bitRate: 8000},
	}
)

func AmrParserParse(audio []byte, isRawStream bool) (*parser.AmrStat, error) {
	return parser.Parse(audio)
}

func GetAudioDur(sId string, path string, audio []byte, inCode int) (float32, error) {
	// AMR‑NB / AMR‑WB 走parser解析
	if inCode == audioformat.AMR_NB.GetCode() || inCode == audioformat.AMR_WB.GetCode() {
		amrStat, err := AmrParserParse(audio, false)
		if err != nil {
			return 0, err
		}
		//logger.Info(fmt.Sprintf(LOG_FORMAT, sId, fmt.Sprintf("getAudioDur,amrParser,path=%s,amrStat=%f", path, amrStat.totalSeconds)))
		return float32(amrStat.TotalSeconds), nil
	}

	var l int64 = 0
	if audio != nil {
		l = int64(len(audio))
	}

	codecInfo, ok := codeTable[inCode]
	if !ok {
		return 0, nil
	}

	secSize := float32(codecInfo.bitRate) / 8.0
	size := l - int64(codecInfo.headSize)
	time := float32(size) / secSize

	//logger.Debug(fmt.Sprintf(LOG_FORMAT, sId, fmt.Sprintf("getAudioDur,secSize=%f,size=%d,time=%f", secSize, size, time)))
	return time, nil
}

func Test() {
	dur, err := GetAudioDur("s123", "/tmp/audio.wav", []byte{1, 2, 3}, audioformat.WAV_PCMU.GetCode())
	if err != nil {
		panic(err)
	}
	fmt.Printf("duration: %.2f s\n", dur)
}
