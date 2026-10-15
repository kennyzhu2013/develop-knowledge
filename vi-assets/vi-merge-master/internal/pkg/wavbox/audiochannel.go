package wavbox

import (
	"os"
)

// 定义Fmt枚举类型
type FmtType int

// 定义Fmt枚举值（使用iota自增）
const (
	FmtS16le FmtType = iota
	FmtPcma
	FmtPcmu

	S16le = "s16le"
	Pcma  = "pcma"
	Pcmu  = "pcmu"
)

const (
	NonCompressed  = 1
	PCMACompressed = 6
	PCMUCompressed = 7
)

type FmtParams struct {
	FrameSampleBytes int
	BitsPerSample    int
	CompressionCode  int
	SilentBytes      []byte
}

var (
	/**
	compressionCode
	 * 1-非压缩格式 ，长度16
	 * 2-Microsoft ADPCM，长度18
	 * 3-IEEE float,长度18
	 * 6-ITU G.711 a-law，长度18
	 * 7-ITU G.711 μ-law，长度18
	 * 49-GSM 6.10  ，长度20
	 * 60-ITU G.721 ADPCM  ，长度未知
	 * 65,534(0xFFFE) ，见子格式块中的编码格式，长度40
	*/

	//frameBytes,每个采样2字节(s16le);如果是G711，这里的值是1，这个值相当于是 bitsPerSample/8
	fmtFrameBytesMap = map[FmtType]FmtParams{
		FmtS16le: {FrameSampleBytes: 2, BitsPerSample: 16, CompressionCode: NonCompressed, SilentBytes: []byte{0x00, 0x00}},
		FmtPcma:  {FrameSampleBytes: 1, BitsPerSample: 8, CompressionCode: PCMACompressed, SilentBytes: []byte{0xd5}},
		FmtPcmu:  {FrameSampleBytes: 1, BitsPerSample: 8, CompressionCode: PCMUCompressed, SilentBytes: []byte{0xff}},
	}
)

/*
**
sampleBytes 跟采样率相关，
frameBytes 跟Bits相关。
*/
func mono16ToStereoDurationLong(leftBytes, rightBytes []byte, params FmtParams) ([]byte, error) {
	/***
	以最常见的 16bit 小端 PCM 为例
	每个采样占 2 字节：
	左单声道：[L0B0, L0B1, L1B0, L1B1, ...]
	右单声道：[R0B0, R0B1, R1B0, R1B1, ...]

	合成后双声道：
	L0B0, L0B1,   R0B0, R0B1,
	L1B0, L1B1,   R1B0, R1B1,
	...

	即每帧占 4 字节：左2字节 + 右2字节。
	--------------------------------------------------
	如果是 8bit 无符号 PCM
	每个采样 1 字节，结构更简单：
	L0, R0, L1, R1, L2, R2...

	代码只需要把 FrameSampleBytes = 1，每帧写 2 字节即可。
	*/

	frameSampleBytes := params.FrameSampleBytes
	leftLen := len(leftBytes)
	rightLen := len(rightBytes)
	numSamples := max(leftLen/frameSampleBytes, rightLen/frameSampleBytes)
	//fmt.Printf("leftLen=%v,rightLen=%v,numSamples=%v\n", leftLen, rightLen, numSamples)

	// s16le,双声道每帧：左2 + 右2 = 4字节
	pcmAc2Data := make([]byte, 0, numSamples*frameSampleBytes*2)

	// 最大只需要2字节，一次性分配永久复用
	buf := make([]byte, 2)
	// 按采样交错合成
	for i := 0; i < numSamples; i++ {
		idx := i * frameSampleBytes
		leftSampleBytes := getTrackBytes(leftBytes, idx, leftLen, params, buf)
		pcmAc2Data = append(pcmAc2Data, leftSampleBytes...)

		rightSampleBytes := getTrackBytes(rightBytes, idx, rightLen, params, buf)
		pcmAc2Data = append(pcmAc2Data, rightSampleBytes...)
	}
	return pcmAc2Data, nil
}

// outBuf 外部复用缓冲区，长度≥params.FrameSampleBytes
func getTrackBytes(tBytes []byte, idx int, trackBytesLen int, params FmtParams, outBuf []byte) []byte {
	switch params.FrameSampleBytes {
	case 1:
		if idx >= trackBytesLen {
			copy(outBuf, params.SilentBytes)
		} else {
			outBuf[0] = tBytes[idx]
		}
		return outBuf[:1]
	case 2:
		// 原来设置为 idx+1 > trackBytesLen 有风险
		if idx+2 > trackBytesLen {
			copy(outBuf, params.SilentBytes)
		} else {
			outBuf[0] = tBytes[idx]
			outBuf[1] = tBytes[idx+1]
		}
		return outBuf[:2]
	}
	return nil
}

func getTrackBytesOld(tBytes []byte, idx int, trackBytesLen int, params FmtParams) []byte {
	var sampleBytes []byte
	if params.FrameSampleBytes == 1 {
		//字节数不匹配，在尾部加静音
		if idx >= trackBytesLen {
			//pcma是d5
			sampleBytes = params.SilentBytes
		} else {
			sampleBytes = []byte{tBytes[idx]}
		}
	} else if params.FrameSampleBytes == 2 {
		//字节数不匹配，在尾部加静音
		if idx+1 > trackBytesLen {
			//左采样率。加入两个静音bytes
			sampleBytes = params.SilentBytes
		} else {
			// 左采样 2 字节
			sampleBytes = []byte{tBytes[idx], tBytes[idx+1]}
		}
	}
	return sampleBytes
}

func testMono16ToStereoShort(leftPath, rightPath string, sampleBytes int, outPath string) error {
	// 1. 读取两个单声道文件
	leftBytes, err := os.ReadFile(leftPath)
	if err != nil {
		return err
	}
	rightBytes, err := os.ReadFile(rightPath)
	if err != nil {
		return err
	}

	//TODO:如果字节数不匹配，在尾部加静音
	numSamples := min(len(leftBytes)/sampleBytes, len(rightBytes)/sampleBytes)

	// 双声道每帧：左2 + 右2 = 4字节
	out := make([]byte, 0, numSamples*4)

	// 2. 按采样交错合成
	for i := 0; i < numSamples; i++ {
		lStart := i * sampleBytes
		rStart := i * sampleBytes

		// 左采样 2 字节
		out = append(out, leftBytes[lStart], leftBytes[lStart+1])
		// 右采样 2 字节
		out = append(out, rightBytes[rStart], rightBytes[rStart+1])
	}

	// 3. 写入输出文件
	return os.WriteFile(outPath, out, 0644)
}
