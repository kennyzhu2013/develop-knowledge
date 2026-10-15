package container

import (
	"errors"
	"fmt"
	"math"
	"regexp"
)

const (
	MVHD = "mvhd"
	TRAK = "trak"
	TKHD = "tkhd"
	//EDTS  = "edts"
	MDIA  = "mdia"
	MEDIA = "media"
	MDHD  = "mdhd"
	HDLR  = "hdlr"
	MINF  = "minf"

	VMHD = "vmhd"
	SMHD = "smhd"
	HMHD = "hmhd"
	DINF = "dinf"

	STBL = "stbl"
	STSD = "stsd"
	STTS = "stts"
	//STSS = "stss"
	STSC = "stsc"
	STSZ = "stsz"
	STCO = "stco"
	CO64 = "co64"

	AVC1  = "avc1"
	MP4A  = "mp4a"
	SAWB  = "sawb"
	AVCC  = "avcC"
	AVCC1 = "avcc"
)

type MovieBox struct {
	/**
	  moov
	*/
	//开始的字节位
	start int

	//结束字节位
	end int

	//视频的偏移量，单视频
	vChunkOffsetBox ChunkOffsetBox

	//音频的偏移量，单声道
	aChunkOffsetBox ChunkOffsetBox

	//隐藏的偏移量
	hChunkOffsetBox []ChunkOffsetBox

	//sps和pps信息
	sps []byte
	pps []byte

	//编码类型
	vCodec string
	aCodec string

	//编码级别信息
	profile int
	level   int

	//视频高宽
	width  int
	height int

	vDuration int
	aDuration int

	soundCount int

	/**
	  video mdhd
	*/
	vMdhd Mdhd

	/**
	  audio mdhd
	*/
	aMdhd []Mdhd

	/**
	video time to sample
	*/
	vTimeSample []TimeToSample

	fps uint32
}

type TimeToSample struct {
	sampleCount int
	sampleDelta int
}

type ChunkOffsetBox struct {
	/**
	  stco
	*/
	mType      string
	enterCount int
	offsets    []int
}

type Mdhd struct {
	createTime       int
	modificationTime int
	timeScale        int
	duration         int
	language         string
}

func (box *MovieBox) parseMoov(bytes []byte) error {
	var err error = nil
	moovIdx := 0
	for moovIdx < len(bytes) {
		dataBytes, boxType, index, _ := BoxTypeData(bytes, moovIdx)
		moovIdx = index
		if boxType == TRAK {
			err = box.parseTrackBox(dataBytes)
		}
	}

	if len(box.vTimeSample) == 1 {
		//这里计算可能帧率30，计算得到29.9这种情况 round一下
		box.fps = uint32(math.Round(float64(box.vMdhd.timeScale / (box.vTimeSample[0].sampleDelta * 1.0))))
	} else {
		maxFps := 0
		for i := 0; i < len(box.vTimeSample); i++ {
			fpsTmp := int(math.Round(float64(box.vMdhd.timeScale / (box.vTimeSample[i].sampleDelta * 1.0))))
			if fpsTmp > maxFps {
				maxFps = fpsTmp
			}
		}
		box.fps = uint32(maxFps)
	}
	return err
}

func (box *MovieBox) parseTrackBox(bytes []byte) error {
	trackIdx := 0
	var err error
	for trackIdx < len(bytes) {
		dataBytes, boxType, index, _ := BoxTypeData(bytes, trackIdx)
		trackIdx = index
		if boxType == TKHD {
			box.parserTkhd(dataBytes)
		} else if boxType == MEDIA || boxType == MDIA {
			err = box.parseMedia(dataBytes)
		} else if IsBadBox(boxType, len(dataBytes)) {
			return errors.New("trak contain bad box")
		}
	}
	return err
}

func (box *MovieBox) parserTkhd(bytes []byte) error {
	if len(bytes) < 80 {
		return errors.New("tkhd len less 80")
	}

	tkhdIdx := 0
	//skip four 4byte property
	tkhdIdx = tkhdIdx + 16

	duration := UFunc(bytes, tkhdIdx*8, 32)
	tkhdIdx = tkhdIdx + 4

	//skip...
	tkhdIdx = tkhdIdx + 52

	//width,4byte
	width := UFunc(bytes, tkhdIdx*8, 16)
	tkhdIdx = tkhdIdx + 2

	//skip  2 bytes
	tkhdIdx = tkhdIdx + 2

	//height,4byte
	height := UFunc(bytes, tkhdIdx*8, 16)
	tkhdIdx = tkhdIdx + 2

	//skip  2 bytes
	tkhdIdx = tkhdIdx + 2

	//记录关心的值
	if width > 0 {
		box.width = width
		box.height = height
		box.vDuration = duration
	} else {
		box.aDuration = duration
	}
	return nil
}

func (box *MovieBox) parseMedia(bytes []byte) error {
	mediaIdx := 0
	var mdhd Mdhd
	typeDesc := ""
	for mediaIdx < len(bytes) {
		dataBytes, boxType, index, _ := BoxTypeData(bytes, mediaIdx)
		mediaIdx = index
		if boxType == MDHD {
			mdhd = box.parserMdhd(dataBytes)
		} else if boxType == MINF {
			typeDesc = box.parseMinf(dataBytes)
		} else if IsBadBox(boxType, len(dataBytes)) {
			return errors.New("mdia contain bad box ")
		}
	}

	if typeDesc == VMHD {
		box.vMdhd = mdhd
	} else if typeDesc == SMHD {
		box.aMdhd = append(box.aMdhd, mdhd)
	}
	return nil
}

func (box *MovieBox) parserMdhd(bytes []byte) Mdhd {
	if len(bytes) < 20 {
		return Mdhd{}
	}
	mdhdStart := 0
	creationTime := UFunc(bytes, mdhdStart*8, 32)
	mdhdStart += 4

	modificationTime := UFunc(bytes, mdhdStart*8, 32)
	mdhdStart += 4

	timescale := UFunc(bytes, mdhdStart*8, 32)
	mdhdStart += 4

	duration := UFunc(bytes, mdhdStart*8, 32)
	mdhdStart += 2

	language := string(bytes[mdhdStart : mdhdStart+2])
	mdhdStart += 2

	return Mdhd{
		createTime:       creationTime,
		modificationTime: modificationTime,
		timeScale:        timescale,
		duration:         duration,
		language:         language,
	}
}

func (box *MovieBox) parseMinf(bytes []byte) string {
	minfIdx := 0
	typeDesc := ""
	for minfIdx < len(bytes) {
		dataBytes, boxType, index, _ := BoxTypeData(bytes, minfIdx)
		minfIdx = index
		if boxType == VMHD {
			typeDesc = VMHD
		} else if boxType == SMHD {
			typeDesc = SMHD
			box.soundCount++
		} else if boxType == HMHD {
			typeDesc = HMHD
		} else if boxType == STBL {
			box.parseStbl(dataBytes, typeDesc)
		} else if IsBadBox(boxType, len(dataBytes)) {
			return ""
		}
	}
	return typeDesc
}

func (box *MovieBox) parseStbl(bytes []byte, typeDesc string) {
	stblIdx := 0
	for stblIdx < len(bytes) {
		dataBytes, boxType, index, _ := BoxTypeData(bytes, stblIdx)
		stblIdx = index
		if boxType == STSD {
			box.parserStsd(dataBytes, typeDesc)
		} else if boxType == STTS {
			box.parserStts(dataBytes, typeDesc)
		} else if boxType == STCO {
			entryCount, offsets := box.parseStco(dataBytes, 32)
			box.storeChunkOffset(entryCount, offsets, typeDesc)
		} else if boxType == CO64 {
			entryCount, offsets := box.parseStco(dataBytes, 64)
			box.storeChunkOffset(entryCount, offsets, typeDesc)
		} else if IsBadBox(boxType, len(dataBytes)) {
			return
		}
	}
}

func (box *MovieBox) parserStts(bytes []byte, typeDesc string) {
	if len(bytes) < 4 {
		return
	}

	sttsIdx := 0
	entryCount := UFunc(bytes, sttsIdx*8, 32)
	sttsIdx += 4

	var time2Samples []TimeToSample
	if (len(bytes) - 4) >= entryCount*8 {
		for entryCount > 0 {
			sampleCount := UFunc(bytes, sttsIdx*8, 32)
			sttsIdx += 4

			sampleDelta := UFunc(bytes, sttsIdx*8, 32)
			sttsIdx += 4

			time2Samples = append(time2Samples, TimeToSample{sampleCount: sampleCount, sampleDelta: sampleDelta})
			entryCount--
		}
	}

	if typeDesc == VMHD {
		box.vTimeSample = time2Samples
	}
}

func (box *MovieBox) parserStsd(bytes []byte, typeDesc string) {
	if len(bytes) < 4 {
		return
	}
	stsdIdx := 0
	stsdIdx += 4

	if typeDesc == VMHD {
		dataBytes, boxType, index, _ := BoxTypeData(bytes, stsdIdx)
		stsdIdx = index
		if boxType == AVC1 {
			box.parseAvc(dataBytes)
		}
	} else if typeDesc == SMHD {
		dataBytes, boxType, index, _ := BoxTypeData(bytes, stsdIdx)
		stsdIdx = index
		box.aCodec = boxType
		if boxType == SAWB {
			box.parseSawb(dataBytes)
		}
	}
}

func (box *MovieBox) parseSawb(_ []byte) {

}

func (box *MovieBox) parseAvc(bytes []byte) {
	if len(bytes) < 97 {
		return
	}
	avcIdx := 0
	//skip
	avcIdx += 82
	vCodec := string(bytes[avcIdx : avcIdx+4])
	avcIdx += 4

	if vCodec == AVCC || vCodec == AVCC1 {
		//configuration version  1 byte default:0x01
		avcIdx += 1

		//avc profile indication 1 byte default:0x00
		profileIndication := UFunc(bytes, avcIdx*8, 8)
		avcIdx += 1

		//skip profile compatibility 1 byte
		avcIdx += 1

		//avc level indication 1 byte H.264 codec level
		levelIndication := UFunc(bytes, avcIdx*8, 8)
		avcIdx += 1

		//reserved 6bit default 1
		//length size minus one , 2bit ,6+2 =8
		avcIdx += 1

		//reserved 3bit
		//number of sps ,5 bit ,3+5 ==8
		avcIdx += 1

		//sps length 2 byte，sps len
		spsLength := UFunc(bytes, avcIdx*8, 16)
		avcIdx += 2

		//sps ,sps length,sps content
		sps := bytes[avcIdx : avcIdx+spsLength]
		avcIdx += spsLength

		// pps front 3bit ,
		//number of pps , 5bit ，3+5
		avcIdx += 1

		//pps length ,2 bytes，pps len
		ppsLength := UFunc(bytes, avcIdx*8, 16)
		avcIdx += 2

		pps := bytes[avcIdx : avcIdx+ppsLength]
		box.sps = sps
		box.pps = pps
		box.profile = profileIndication
		box.vCodec = AVCC
		box.level = levelIndication
	}
}

func (box *MovieBox) parseStco(bytes []byte, bit int) (int, []int) {
	if len(bytes) < 4 {
		return 0, nil
	}
	stcoIdx := 0
	entryCount := UFunc(bytes, stcoIdx*8, bit)
	stcoIdx += 4
	var chunkOffsetList []int
	for stcoIdx < len(bytes) {
		chunkOffset := UFunc(bytes, stcoIdx*8, bit)
		stcoIdx += 4
		chunkOffsetList = append(chunkOffsetList, chunkOffset)
	}
	return entryCount, chunkOffsetList
}

func (box *MovieBox) storeChunkOffset(entryCount int, offsets []int, mType string) {
	offsetBox := ChunkOffsetBox{
		enterCount: entryCount,
		offsets:    offsets,
		mType:      mType,
	}
	if mType == VMHD {
		box.vChunkOffsetBox = offsetBox
	} else if mType == SMHD {
		box.aChunkOffsetBox = offsetBox
	} else if mType == HMHD {
		box.hChunkOffsetBox = append(box.hChunkOffsetBox, offsetBox)
	}
}

const (
	BoxMinLen = 8
)

func UFunc(pBuff []byte, startBit int, bitCount int) int {
	dwRet := 0
	for i := 0; i < bitCount; i++ {
		dwRet <<= 1
		if (pBuff[startBit/8] & (0x80 >> (startBit % 8))) != 0 {
			dwRet += 1
		}
		startBit++
	}
	return dwRet
}

func BoxTypeData(bytes []byte, startByte int) ([]byte, string, int, int) {
	var dataBytes []byte
	lenBytes := len(bytes)
	if startByte+BoxMinLen > lenBytes {
		return nil, "", 0, 0
	}

	boxSize := UFunc(bytes, startByte*8, 32)
	startByte += 4
	boxType := string(bytes[startByte : startByte+4])
	startByte += 4

	headSize := BoxMinLen
	if boxSize == 0 {
		boxSize = UFunc(bytes, startByte*8, 64)
		startByte += 8
		headSize += 8
	}

	if IsFullBox(boxType) {
		startByte += 1
		startByte += 3
		headSize += 4
	}

	if boxSize != 1 {
		dataSize := boxSize - headSize
		if (startByte+dataSize) <= lenBytes && dataSize > 0 {
			dataBytes = bytes[startByte : startByte+dataSize]
			startByte += dataSize
		} else {
			//print log
			//fmt.Printf("%s,startByte+dataSize > lenBytes\n", boxType)
		}
	} else {
		dataBytes = bytes[startByte:]
		startByte += len(dataBytes)
	}
	return dataBytes, boxType, startByte, headSize
}

func FindNextValue(arr []int, find int) int {
	arrLen := len(arr)
	leftIndex := 0
	rightIndex := arrLen - 1

	if arrLen < 1 {
		return -1
	}

	if arr[0] > find {
		return arr[0]
	}

	for {
		middleIndex := (leftIndex + rightIndex) / 2
		if leftIndex > rightIndex {
			break
		} else {
			v := arr[middleIndex]
			nextV := -1
			if middleIndex+1 < len(arr) {
				nextV = arr[middleIndex+1]
			}

			if v < find && nextV > find {
				//fmt.Printf("i=%v,nextV=%v\n", middleIndex+1, nextV)
				return nextV
			} else if v > find {
				//left find
				rightIndex = middleIndex - 1
			} else {
				//right find
				leftIndex = middleIndex + 1
			}
		}
	}
	return -1
}

func IsBadBox(boxType string, dataLen int) bool {
	match, _ := regexp.MatchString("[a-zA-Z]+", boxType)
	if dataLen == 0 && !match {
		return true
	}
	return false
}

func IsFullBox(boxType string) bool {
	if boxType == MVHD || boxType == TKHD || boxType == "elst" ||
		boxType == MDHD || boxType == HDLR || boxType == VMHD ||
		boxType == SMHD || boxType == "dref" || boxType == STSD ||
		boxType == STTS || boxType == "ctts" || boxType == STSC ||
		boxType == STSZ || boxType == "stz2" || boxType == STCO ||
		boxType == CO64 {
		return true
	}
	return false
}

const (
	FTYP = "ftyp"
	MDAT = "mdat"
	MOOV = "moov"
)

var StartCode = []byte{0x00, 0x00, 0x00, 0x01}
var WbHeader = []byte{0x23, 0x21, 0x41, 0x4d, 0x52, 0x2d, 0x57, 0x42, 0x0a}

type Box struct {
	fileTypeBox FileTypeBox

	mediaDataBox MediaDataBox

	movieBox MovieBox
}

type FileTypeBox struct {
	ftypSize         int
	boxType          string
	majorBrand       string
	minorVersion     int
	compatibleBrands string
}

type MediaDataBox struct {
	/**
	mdat
	*/

	//开始的字节位
	start int

	//结束字节位
	end int

	/**
	  视频数据
	*/
	videoBytes []byte

	/**
	  音频数据
	*/
	audioBytes []byte
}

type V3gp struct {

	// 视频数据，h264数据
	Video []byte

	// 音频数据，般是wb数据
	Audio []byte

	// 规格
	Profile int

	// 级别
	Level int

	// 视频高宽
	Width  int
	Height int

	// 时长,ms
	Duration int

	// 帧率
	Fps uint32
}

// Demux3GP 返回err情况下仍然可以查看h264、wb是否ok
func Demux3GP(data []byte) (*V3gp, error) {
	box := new(Box)
	video, audio, err := box.demux3gp(data)
	if err != nil {
		return nil, err
	}
	if len(video) == 0 {
		return nil, errors.New("empty video")
	}
	return &V3gp{
		Video:    video,
		Audio:    audio,
		Profile:  box.movieBox.profile,
		Level:    box.movieBox.level,
		Width:    box.movieBox.width,
		Height:   box.movieBox.height,
		Duration: box.movieBox.vDuration,
		Fps:      box.movieBox.fps,
	}, nil
}

func (box *Box) demux3gp(data []byte) ([]byte, []byte, error) {
	totalLen := len(data)
	byteIdx, err := box.parseFileTypeBox(data, 0)
	if err != nil {
		return nil, nil, errors.New("ftyp error")
	}

	for byteIdx < totalLen {
		dataBytes, boxType, curIdx, _ := BoxTypeData(data, byteIdx)
		dataLen := len(dataBytes)
		byteIdx = curIdx
		if byteIdx > totalLen {
			return nil, nil, errors.New(fmt.Sprintf("byteIdx(%v)>totalLen(%v)", byteIdx, totalLen))
		}
		if boxType == MDAT {
			if dataLen == 0 {
				return nil, nil, errors.New("MDAT data size is zero")
			}
			box.mediaDataBox.end = byteIdx
			box.mediaDataBox.start = byteIdx - len(dataBytes)
		} else if boxType == MOOV {
			box.movieBox.end = byteIdx
			box.movieBox.start = byteIdx - len(dataBytes)
			err = box.parseMoov(dataBytes)
		}
	}

	if box.movieBox.vCodec == AVCC {
		box.obtainH264Data(data)
	} else {
		err = errors.New("vCode error")
	}

	if box.movieBox.aCodec == SAWB && box.movieBox.soundCount == 1 {
		box.obtainWbData(data)
	} else {
		_ = errors.New(fmt.Sprintf("aCodeError(%v),channlesError(%v)\n", box.movieBox.aCodec, box.movieBox.soundCount))
	}
	return box.mediaDataBox.videoBytes, box.mediaDataBox.audioBytes, err
}

func (box *Box) parseFileTypeBox(bytes []byte, curByteIdx int) (int, error) {
	if len(bytes) < 16 {
		return 0, errors.New("ftyp len(bytes) < 16")
	}

	ftypSize := UFunc(bytes, curByteIdx*8, 32)
	curByteIdx += 4
	box.fileTypeBox.ftypSize = ftypSize

	boxType := string(bytes[4:8])
	curByteIdx += 4
	if boxType != FTYP {
		return 0, errors.New("boxType not ftyp")
	}
	box.fileTypeBox.boxType = boxType

	box.fileTypeBox.majorBrand = string(bytes[8:12])
	curByteIdx += 4

	box.fileTypeBox.minorVersion = UFunc(bytes, curByteIdx*8, 32)
	curByteIdx += 4

	if ftypSize+16 < len(bytes) {
		box.fileTypeBox.compatibleBrands = string(bytes[16:ftypSize])
		curByteIdx += box.fileTypeBox.ftypSize - 16
	}
	return curByteIdx, nil
}

func (box *Box) obtainH264Data(bytes []byte) {
	vOffsets := box.movieBox.vChunkOffsetBox.offsets
	var videoBytes []byte
	for vIndex, vChunkStart := range vOffsets {
		vChunkEnd := box.getChunkEnd(vIndex, true)
		for vChunkStart < vChunkEnd {
			naluLength := UFunc(bytes, vChunkStart*8, 32)
			vChunkStart += 4
			naluHeader := UFunc(bytes, vChunkStart*8, 8)
			naluType := naluHeader & 0x1F

			if vChunkStart+naluLength > vChunkEnd {
				break
			}

			readDatas := bytes[vChunkStart : vChunkStart+naluLength]
			vChunkStart += naluLength
			if naluType == 5 {
				sps := box.movieBox.sps
				pps := box.movieBox.pps
				videoBytes = append(videoBytes, StartCode...)
				videoBytes = append(videoBytes, sps...)
				videoBytes = append(videoBytes, StartCode...)
				videoBytes = append(videoBytes, pps...)
			}
			videoBytes = append(videoBytes, StartCode...)
			videoBytes = append(videoBytes, readDatas...)
		}
	}
	box.mediaDataBox.videoBytes = videoBytes
}

func (box *Box) obtainWbData(bytes []byte) {
	aOffsets := box.movieBox.aChunkOffsetBox.offsets
	var soundBytes []byte
	soundBytes = append(soundBytes, WbHeader...)

	for aIndex, aChunkStart := range aOffsets {
		aChunkEnd := box.getChunkEnd(aIndex, false)
		for aChunkStart < aChunkEnd {
			readDatas := bytes[aChunkStart:aChunkEnd]
			aChunkStart += aChunkEnd
			soundBytes = append(soundBytes, readDatas...)
		}
	}

	box.mediaDataBox.audioBytes = soundBytes
}

func (box *Box) getChunkEnd(index int, isVideo bool) int {
	var firstOffsets []int
	var secondOffsets []int
	if isVideo {
		firstOffsets = box.movieBox.vChunkOffsetBox.offsets
		secondOffsets = box.movieBox.aChunkOffsetBox.offsets
	} else {
		firstOffsets = box.movieBox.aChunkOffsetBox.offsets
		secondOffsets = box.movieBox.vChunkOffsetBox.offsets
	}
	firstCount := len(firstOffsets)
	chunkStart := firstOffsets[index]
	firstNext := -1
	chunkEnd := -1

	if index+1 < firstCount {
		firstNext = firstOffsets[index+1]
		chunkEnd = firstNext
	}

	secondNext := FindNextValue(secondOffsets, chunkStart)
	if chunkEnd == -1 {
		chunkEnd = secondNext
	} else if secondNext > chunkStart && secondNext < chunkEnd {
		chunkEnd = secondNext
	}

	hNext := -1
	for _, hs := range box.movieBox.hChunkOffsetBox {
		next := FindNextValue(hs.offsets, chunkStart)
		if hNext == -1 {
			hNext = next
		} else if next < hNext {
			hNext = next
		}
	}

	if chunkEnd == -1 {
		chunkEnd = hNext
	} else if hNext > chunkStart && hNext < chunkEnd {
		chunkEnd = hNext
	}

	if index == firstCount-1 && chunkEnd == -1 {
		mdatEnd := box.mediaDataBox.end
		if chunkStart < mdatEnd {
			chunkEnd = mdatEnd
		}
	}

	return chunkEnd
}

func (box *Box) parseMoov(dataBytes []byte) error {
	movieBox := &MovieBox{}
	movieBox.soundCount = 0
	err := movieBox.parseMoov(dataBytes)
	box.movieBox = *movieBox
	return err
}