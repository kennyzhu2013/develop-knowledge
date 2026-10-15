package wavbox

import (
	"bytes"
	"errors"
	"vi-merge/internal/pkg/log"
)

/*
**
读取wav的头部
*/
func wavHeader(dataLen int, compressionCode, numChannels, sampleRate, bitsPerSample int) ([]byte, error) {
	if dataLen < 1 {
		return nil, errors.New("data empty")
	}

	/**
	 * riff-chunk ,4+4+4
	 */
	riffChunkSize := 12

	/**
	 * fotmatSize ,fmt-chunk
	 */
	formatSize := getHeaderFormatSize(compressionCode)

	/**
	 * formatSize+(subchunk1ID(4byte)+subchunk1Size(4byte))
	 */
	fmtChunkSize := formatSize + 8

	/**
	 * face-chunk不一定存在，大小4+4+4=12
	 */
	factChunkSize := 0
	//压缩编码格式有fact字段
	if compressionCode == 6 || compressionCode == 7 {
		factChunkSize = 12
	}

	/**
	 * data-chunk,4+4
	 */
	dataChunkSize := 8
	headerSize := riffChunkSize + fmtChunkSize + factChunkSize + dataChunkSize
	//log.InfoF("headerSize=%v", headerSize)

	//------riff-chunk------
	var buf bytes.Buffer
	riff := "RIFF"
	format := "WAVE"

	riffSize := headerSize - 8 + dataLen
	buf.Write([]byte(riff))
	buf.Write(intToByteArray(riffSize))
	buf.Write([]byte(format))

	//log.InfoF("riff-chunk,riff=%v,riffSize=%v,format=%v", riff, riffSize, format)

	//-----fmt-chunk-------
	subChunk1ID := "fmt "
	buf.Write([]byte(subChunk1ID))

	//format size,default 16
	buf.Write(intToByteArray(formatSize))

	//compressionCode
	buf.Write(shortToByteArray(compressionCode))

	//numChannels
	buf.Write(shortToByteArray(numChannels))

	//sampleRate
	buf.Write(intToByteArray(sampleRate))

	//bytesPerSecond/bitRate,声道数×采样频率×每样本的数据位数/8 单位是B
	bytesPerSecond := numChannels * sampleRate * bitsPerSample / 8
	buf.Write(intToByteArray(bytesPerSecond))

	//blockAlign numChannels*位数/8
	blockAlign := numChannels * bitsPerSample / 8
	buf.Write(shortToByteArray(blockAlign))

	//bitsPerSample
	buf.Write(shortToByteArray(bitsPerSample))

	//扩充字段用空字符代替
	gap := 0
	if formatSize != 16 {
		gap = formatSize - 16
		for j := 0; j < gap; j++ {
			buf.Write([]byte(" "))
		}
	}
	//log.InfoF("fmt-chunk=%v,formatSize=%v,compressionCode=%v,numChannels=%v,sampleRate=%v,bytesPerSecond=%v,blockAlign=%v,bitsPerSample=%v,extLen=%v",
	//	subChunk1ID, formatSize, compressionCode, numChannels, sampleRate, bytesPerSecond, blockAlign, bitsPerSample, gap)

	//---------fact-chunk-------------
	if compressionCode == 6 || compressionCode == 7 {
		fact := "fact"
		factSize := 4

		buf.Write([]byte(fact))
		buf.Write(intToByteArray(factSize))

		//writeString(bos, fact)
		//writeLong(bos, factSize)

		sampleSum := dataLen / bitsPerSample / numChannels
		//writeLong(bos, sampleSum)
		buf.Write(intToByteArray(sampleSum))

		log.InfoF("fact-chunk=%v,factSize=%v,sampleSum=%v ", fact, factSize, sampleSum)

	}

	//-------data-chunk-------
	dataFlag := "data"
	//writeString(bos, dataFlag)
	buf.Write([]byte(dataFlag))
	//writeLong(bos, dataLen)
	buf.Write(intToByteArray(dataLen))
	//log.InfoF("data-chunk:%v,dataLen=%v", dataFlag, dataLen)

	return buf.Bytes(), nil
}

func intToByteArray(integer int) []byte {
	// 初始化4字节的字节数组（对应Java的new byte[4]）
	result := make([]byte, 4)
	result[3] = byte((integer >> 24) & 0xff)
	result[2] = byte((integer >> 16) & 0xff)
	result[1] = byte((integer >> 8) & 0xff)
	result[0] = byte(integer & 0xff)
	return result
}

func shortToByteArray(v int) []byte {
	result := make([]byte, 2)
	result[1] = byte((v >> 8) & 0xff)
	result[0] = byte(v & 0xff)
	return result
}

func getHeaderFormatSize(compressionCode int) int {
	formatSize := 16
	if compressionCode == 1 {
		//PCM/非压缩格式
		formatSize = 16
	} else if compressionCode == 2 {
		//Microsoft ADPCM
		formatSize = 18
	} else if compressionCode == 3 {
		//IEEE float
		formatSize = 18
	} else if compressionCode == 6 {
		//ITU G.711 a-law
		formatSize = 18
	} else if compressionCode == 7 {
		//ITU G.711 μ-law
		formatSize = 18
	} else if compressionCode == 49 {
		//GSM 6.10
		formatSize = 20
	} else if compressionCode >= 65 && compressionCode <= 534 {
		formatSize = 40
	}
	return formatSize
}
