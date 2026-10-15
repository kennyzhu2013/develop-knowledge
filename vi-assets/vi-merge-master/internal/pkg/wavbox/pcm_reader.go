package wavbox

import (
	"encoding/binary"
	"errors"
	"strings"
	"vi-merge/internal/pkg/log"
)

/**
读取头部和数据pcm
*/

const (
	LenChunkDescriptor = 4
	LenWaveFlag        = 4
	LenFmtUbChunk      = 4
	LenDataSubChunk    = 4

	Riff = "RIFF"
)

type WavHead struct {
	chunkDescriptor string
	waveFlag        string
	fmtUbChunk      string
	subChunk1Size   uint32
	audioFormat     uint16
	numChannels     uint16
	sampleRate      uint32
	byteRate        uint32
	blockAlign      uint16
	bitsPerSample   uint16
	dataSubChunk    string
	factSize        uint32
	sampleSum       uint32
	subChunk2Size   uint32
	chunkSize       uint32

	HeadSize int
}

func readWavHeader(wav []byte) (*WavHead, error) {
	wavLen := len(wav)
	if wavLen < 44 {
		return nil, errors.New("wav header less 44")
	}

	offset := 0
	wavHead := &WavHead{}
	wavHead.chunkDescriptor = string(wav[:LenChunkDescriptor])
	offset = offset + LenChunkDescriptor
	if !strings.EqualFold(wavHead.chunkDescriptor, Riff) {
		return nil, errors.New("RIFF miss")
	}

	//[96, 24, 36, 0]
	wavHead.chunkSize = readLong(wav[offset : offset+4])
	offset = offset + 4
	//fmt.Printf("ChunkSize=%v \n", wavHead.chunkSize)

	wavHead.waveFlag = string(wav[offset : offset+LenWaveFlag])
	offset = offset + LenWaveFlag
	//fmt.Printf("waveFlag=%v \n", wavHead.waveFlag)
	if !strings.EqualFold(wavHead.waveFlag, "WAVE") {
		return nil, errors.New("WAVE  miss")
	}

	wavHead.fmtUbChunk = string(wav[offset : offset+LenFmtUbChunk])
	offset = offset + LenFmtUbChunk
	//fmt.Printf("fmtUbChunk=%v \n", wavHead.fmtUbChunk)
	if !strings.EqualFold(wavHead.fmtUbChunk, "fmt ") {
		return nil, errors.New("Fmt  miss")
	}

	wavHead.subChunk1Size = readLong(wav[offset : offset+4])
	//fmt.Printf("subChunk1Size=%v \n", wavHead.subChunk1Size)
	offset = offset + 4

	wavHead.audioFormat = readInt(wav[offset : offset+2])
	offset = offset + 2

	wavHead.numChannels = readInt(wav[offset : offset+2])
	offset = offset + 2

	wavHead.sampleRate = readLong(wav[offset : offset+4])
	offset = offset + 4

	wavHead.byteRate = readLong(wav[offset : offset+4])
	offset = offset + 4

	wavHead.blockAlign = readInt(wav[offset : offset+2])
	offset = offset + 2

	wavHead.bitsPerSample = readInt(wav[offset : offset+2])
	offset = offset + 2

	log.InfoF("FMT Chunk--> fmtUbChunk:%v,subChunk1Size=%v,audioFormat=%v,numChannels=%v,sampleRate=%v,byteRate=%v,blockAlign=%v,bitsPerSample=%v",
		wavHead.fmtUbChunk, wavHead.subChunk1Size, wavHead.audioFormat, wavHead.numChannels,
		wavHead.sampleRate, wavHead.byteRate, wavHead.blockAlign, wavHead.bitsPerSample)

	//fmt-chunk的大小按照字段来算是只有16字节的，但是有可能大于16，直接跳过就好了
	if wavHead.subChunk1Size != 16 {
		gap := int(wavHead.subChunk1Size - 16)
		if gap > 0 {
			fmtExt := string(wav[offset : offset+gap])
			offset = offset + gap
			log.InfoF("gap=%v,fmtExt=%v", gap, fmtExt)
		}
	}

	wavHead.dataSubChunk = string(wav[offset : offset+LenDataSubChunk])
	offset = offset + LenDataSubChunk
	//fmt.Printf("dataSubChunk=%v \n", wavHead.dataSubChunk)

	/**
	 * 解析fact字段
	 * 采用压缩编码(修订版Rev.3以后出现的编码格式)的WAV文件必须有含有fact块。块标识符为“fact”。块长度至少4个字节。
	 * 目前fact块只有一个数据项,为每个声道采样总数,或采样帧总数。该数值可由data块中的数据长度除以数据块对齐单位的数值计算出。
	 * 虽然基于压缩编码的文件含有fact块,然而,实测中发现,将文件转换成PCM编码格式后,原fact块仍然存在
	 *
	 * 块标识   4   ‘fact’
	 * 块长度   4
	 * 采样总数 4    采样总数（每个声道）
	 *
	 * 采样总数 = 数据长度/数据块对其单位数值
	 */
	if strings.EqualFold(wavHead.dataSubChunk, "fact") {
		wavHead.factSize = readLong(wav[offset : offset+4])
		offset = offset + 4

		wavHead.sampleSum = readLong(wav[offset : offset+4])
		offset = offset + 4

		//System.out.println(factSize+","+sampleSum);
		log.InfoF("FACT Chunk-->,dataSubChunk=%v,factSize=%v,sampleSum=%v ", wavHead.dataSubChunk, wavHead.factSize, wavHead.sampleSum)
		wavHead.dataSubChunk = string(wav[offset : offset+LenDataSubChunk])
		offset = offset + LenDataSubChunk
	}

	for {
		if strings.HasSuffix(wavHead.dataSubChunk, "LIST") {
			lengthTemp := int(readLong(wav[offset : offset+4]))
			offset = offset + lengthTemp
			temp := string(wav[offset : offset+lengthTemp])
			offset = offset + 4
			log.InfoF("temp=%v,lengthTemp=%v", temp, lengthTemp)
			wavHead.dataSubChunk = string(wav[offset : offset+LenDataSubChunk])
			offset = offset + LenDataSubChunk
		} else {
			break
		}
	}

	if !strings.EqualFold(wavHead.dataSubChunk, "data") {
		return nil, errors.New("data miss")
	}

	//subChunk2Size就是pcm的数据大小,可能会多出一些尾部数据，不拷贝就ok
	wavHead.subChunk2Size = readLong(wav[offset : offset+4])

	offset = offset + 4
	wavHead.HeadSize = offset
	log.InfoF("subChunk2Size=%v,headSize=%v", wavHead.subChunk2Size, wavHead.HeadSize)
	log.InfoF("DATA Chunk-->dataSubChunk=%v,subChunk2Size=%v", wavHead.dataSubChunk, wavHead.subChunk2Size)

	return wavHead, nil
}

func readInt(buf []byte) uint16 {
	return binary.LittleEndian.Uint16(buf)
}

func readLong(buf []byte) uint32 {
	return binary.LittleEndian.Uint32(buf)
}

func littleEndianUint32(buf []byte) uint32 {
	return uint32(buf[3])<<24 | uint32(buf[2])<<16 | uint32(buf[1])<<8 | uint32(buf[0])
}

func bigEndianUint32(buf []byte) uint32 {
	return uint32(buf[0])<<24 | uint32(buf[1])<<16 | uint32(buf[2])<<8 | uint32(buf[3])
}
