package audioformat

import (
	"io"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/util"
	"os"
	"strings"
)

type FormatDetector interface {
	Detect(header []byte) bool
	GetFormat() AudioFormat
}

var detectors = []FormatDetector{
	// AMR-WB
	&amrWbDetector{},

	// AMR-NB
	&amrNbDetector{},

	// WAV
	&wavDetector{},
}

type amrWbDetector struct{}

func (d *amrWbDetector) Detect(header []byte) bool {
	return containsString(header, "AMR-WB")
}

func (d *amrWbDetector) GetFormat() AudioFormat {
	return AMR_WB
}

type amrNbDetector struct {
	header []byte
}

func (d *amrNbDetector) Detect(header []byte) bool {
	d.header = header
	return containsString(header, "AMR") && !containsString(header, "AMR-WB")
}

func (d *amrNbDetector) GetFormat() AudioFormat {
	if len(d.header) > 7 {
		if d.header[6] != 0x3c {
			return AMR_FT_NO_FIT
		}
	}
	return AMR_NB
}

type wavDetector struct {
	header []byte
}

func (d *wavDetector) Detect(header []byte) bool {
	d.header = header
	return containsString(header, "RIFF")
}

func (d *wavDetector) GetFormat() AudioFormat {
	if len(d.header) >= 21 {
		switch d.header[20] {
		case 6:
			return WAV_PCMA
		case 7:
			return WAV_PCMU
		case 1:
			return WAV_NO_COMPRESS
		}
	}
	return UNKNOWN
}

// ------------------接口---------------------------
const (
	PCM_SUFFIX = ".pcm"
	G729Dot    = ".g729"
	S16Le      = "S16LE"
	F32Le      = "F32LE"
)

func detectFormat(filePath string) AudioFormat {
	if !util.IsFileExist(filePath) {
		return UNKNOWN
	}

	header := readFileHeader(filePath, 21)
	if header == nil {
		return UNKNOWN
	}

	for _, d := range detectors {
		if d.Detect(header) {
			return d.GetFormat()
		}
	}
	return UNKNOWN
}

func detectFormatBytes(src []byte) AudioFormat {
	l := 21
	//dst := make([]byte, 21) // 先分配目标空间
	//n := copy(dst, src[0:l])
	//header :=  dst

	header := append([]byte(nil), src[0:l]...)

	if header == nil {
		return UNKNOWN
	}

	for _, d := range detectors {
		if d.Detect(header) {
			return d.GetFormat()
		}
	}
	return UNKNOWN
}

func readFileHeader(filePath string, length int) []byte {
	file, err := os.Open(filePath)
	if err != nil {
		log.WarnK("10086", "open file err:", err)
		return nil
	}
	defer file.Close()

	header := make([]byte, length)
	n, err := file.Read(header)
	if err != nil && err != io.EOF {
		return nil
	}
	if n <= 0 {
		return nil
	}
	return header[:n]
}

func containsString(data []byte, str string) bool {
	return strings.Contains(string(data), str)
}

// 对外接口
func GetHandleCode(filePath string, pcmAttr models.PcmAttr) int {
	lowerSuffix := strings.ToLower(filePath)
	if strings.HasSuffix(lowerSuffix, PCM_SUFFIX) {
		format := pcmAttr.Format
		sample := pcmAttr.Sample
		if format == S16Le {
			if sample == 16000 {
				return PCM_S16_16K.GetCode()
			}
			return PCM_S16_8K.GetCode()
		} else if format == F32Le {
			if sample == 16000 {
				return PCM_F32_16K.GetCode()
			}
			return PCM_F32_8K.GetCode()
		}
		return UNKNOWN.GetCode()
	} else if strings.HasSuffix(lowerSuffix, G729Dot) {
		return G729.GetCode()
	}

	return detectFormat(filePath).GetCode()
}

func GetHandleCodeByte(filePath string, bs []byte, pcmAttr models.PcmAttr) int {
	lowerSuffix := strings.ToLower(filePath)
	if strings.HasSuffix(lowerSuffix, PCM_SUFFIX) {
		format := pcmAttr.Format
		sample := pcmAttr.Sample
		if format == S16Le {
			if sample == 16000 {
				return PCM_S16_16K.GetCode()
			}
			return PCM_S16_8K.GetCode()
		} else if format == F32Le {
			if sample == 16000 {
				return PCM_F32_16K.GetCode()
			}
			return PCM_F32_8K.GetCode()
		}
		return UNKNOWN.GetCode()
	} else if strings.HasSuffix(lowerSuffix, G729Dot) {
		return G729.GetCode()
	}

	return detectFormatBytes(bs).GetCode()
}
