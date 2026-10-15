package processor

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"math"
	"os"
	"vi-merge/conf"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/pkg/sysmem"
	"vi-merge/internal/session"
	"vi-merge/internal/util"
)

const (
	BufWriterSize = 2 * 1024 * 1024

	ReadBufSize = 4 * 1024 * 1024

	ByteBufferType = "ByteBuffer"

	//256kbps的1分钟大小
	Buffer2DiskMinuteThreshold = 1920000

	MemCacheUpdateSec = 30
)

var Buffer2DiskThreshold = 0

type FileBuffer interface {
	Create() error
	BufferWrite(pcm []byte) (error, bool)
	BufferLen() int
	BufferFlush() error
	BufferRead() ([]byte, error)
	BufferFile() (string, error)
	BufferClose() error
}

func NewFileBuffer(bType string, sId string, date string) FileBuffer {
	if Buffer2DiskThreshold < 100 {
		Buffer2DiskThreshold = conf.App.Buffer2DiskMinutes * Buffer2DiskMinuteThreshold
		log.DebugK(sId, "NewFileBuffer,Buffer2DiskThreshold=%v", Buffer2DiskThreshold)
	}

	if bType == ByteBufferType {
		return NewByteBufferIO(sId, date)
	}

	bufWriterFlushThreshold := int(math.Ceil(0.8 * float64(BufWriterSize)))
	return NewFileBufIO(sId, BufWriterSize, bufWriterFlushThreshold, date)
}

func ByteBuffer2FileBuffer(sId string, byteBuffer FileBuffer) (FileBuffer, error) {
	if bBuffer, ok := byteBuffer.(*ByteBufferIO); ok {
		datas, err := bBuffer.BufferRead()
		if err != nil {
			return nil, fmt.Errorf("ByteBufferIO,BufferRead,err=%v", err)
		}
		bufIoThreshold := len(datas)
		if bufIoThreshold < BufWriterSize {
			bufIoThreshold = BufWriterSize
		}
		bufWriterSize := int(1.2 * float64(bufIoThreshold))
		fileBuffer := &FileBufIO{
			sId:            sId,
			fileWriteCnt:   0,
			bufIoSize:      bufWriterSize,
			bufIoThreshold: bufIoThreshold,
			timeDate:       bBuffer.timeDate,
		}

		err = fileBuffer.Create()
		if err != nil {
			fileBuffer.BufferClose()
			return nil, fmt.Errorf("ByteBufferIO,Create,err=%v", err)
		}

		err, _ = fileBuffer.BufferWrite(datas)
		if err != nil {
			fileBuffer.BufferClose()
			return nil, fmt.Errorf("FileBuffer,BufferWrite=%v", err)
		}

		bBuffer.BufferClose()
		return fileBuffer, nil
	}
	return nil, fmt.Errorf("file buffer is not a ByteBufferIO")
}

// ---------------------------------------------------------

func NewByteBufferIO(id string, date string) FileBuffer {
	return &ByteBufferIO{
		sId:       id,
		initFin:   false,
		timeDate:  date,
		pcmBuffer: new(bytes.Buffer),
	}
}

type ByteBufferIO struct {
	sId       string
	pcmBuffer *bytes.Buffer
	initFin   bool
	timeDate  string
}

func (b *ByteBufferIO) BufferFile() (string, error) {
	return "", errors.New("not file exists")

}

func (b *ByteBufferIO) Create() error {
	if !b.initFin {
		b.pcmBuffer.Reset()
		b.pcmBuffer.Grow(32000)
		b.initFin = true
	}
	return nil
}

func (b *ByteBufferIO) BufferWrite(pcm []byte) (error, bool) {
	if b.pcmBuffer == nil {
		return fmt.Errorf("pcmBuffer is nil"), false
	}
	n, err := b.pcmBuffer.Write(pcm)
	if n != len(pcm) {
		log.WarnK(b.sId, "ByteBufferIO,Write,n(%v)!= len(%v)", n, len(pcm))
	}

	fileLen := b.pcmBuffer.Len()
	ok := is2Disk(b.sId, fileLen)
	return err, ok
}

func (b *ByteBufferIO) BufferFlush() error {
	return nil
}

func (b *ByteBufferIO) BufferLen() int {
	if b.pcmBuffer == nil {
		return 0
	}
	return b.pcmBuffer.Len()
}

func (b *ByteBufferIO) BufferRead() ([]byte, error) {
	if b.pcmBuffer == nil {
		return nil, fmt.Errorf("pcmBuffer is nil")
	}
	pcmLen := b.pcmBuffer.Len()
	pcmAc2DataAll := make([]byte, pcmLen)
	readN, err := b.pcmBuffer.Read(pcmAc2DataAll)
	if readN != len(pcmAc2DataAll) {
		log.WarnK(b.sId, "ByteBufferIO,Read,readN(%v)!= len(%v)", readN, len(pcmAc2DataAll))
	}
	return pcmAc2DataAll, err
}

func (b *ByteBufferIO) BufferClose() error {
	b.pcmBuffer.Reset()
	b.pcmBuffer = nil
	return nil
}

//-------------------------------------------------

type FileBufIO struct {
	sId string

	//本地文件写入
	file      *os.File
	bufWriter *bufio.Writer

	bufIoSize      int
	bufIoThreshold int

	timeDate string

	fileWriteCnt int
	//全部合音文件的路径
	finalPcmPath string
	finalPcmDir  string
}

func NewFileBufIO(id string, size, threshold int, date string) FileBuffer {
	return &FileBufIO{
		sId:            id,
		fileWriteCnt:   0,
		bufIoSize:      size,
		bufIoThreshold: threshold,
		timeDate:       date,
	}
}

func (f *FileBufIO) Create() error {
	if f.file == nil {
		dir, err := util.CreateDataDir(f.sId, f.timeDate)
		if err != nil {
			return fmt.Errorf("FileBufIO,Create,err=%v", err)
		}
		f.finalPcmDir = dir
		f.finalPcmPath = fmt.Sprintf("%v/%v.pcm", dir, f.sId)

		//如果有旧文件要删除
		_ = os.Remove(f.finalPcmPath)
		//if err != nil {
		//	log.InfoK(f.SessionId, "FileBufIO,recordPcm,Remove old fail,err=%v", err)
		//}

		log.InfoK(f.sId, "FileBufIO,file,init")
		//f.file, err = util.OpenFile(f.finalPcmPath, true)

		f.file, err = os.OpenFile(f.finalPcmPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
		if err != nil {
			//log.WarnK(f.sId, "FileBufIO,recordPcm,err=%v", err)
			return fmt.Errorf("FileBufIO,recordPcm,err=%v", err)
		}
		// 1MB 自定义缓冲，大幅减少系统调用
		f.bufWriter = bufio.NewWriterSize(f.file, f.bufIoSize)
	}

	return nil
}

func (f *FileBufIO) BufferWrite(pcms []byte) (error, bool) {
	if f.bufWriter == nil {
		return fmt.Errorf("FileBufIO,BufferWrite,not init"), false
	}
	_, err := f.bufWriter.Write(pcms)
	if err != nil {
		return fmt.Errorf("FileBufIO,Writer Write,err=%v", err), false
	}
	if f.bufWriter.Buffered() >= f.bufIoThreshold {
		err = f.bufWriter.Flush()
		if err != nil {
			return fmt.Errorf("FileBufIO,Writer Flush,err=%v", err), false
		}
		f.fileWriteCnt += f.bufWriter.Buffered()
	}
	return err, false
}

func (f *FileBufIO) BufferFlush() error {
	err := f.flushAndSync()
	if err != nil {
		log.WarnK(f.sId, "WavAc2Processor,file.Sync,err=%v", err)
	}
	return f.file.Close()
}

func (f *FileBufIO) BufferLen() int {
	unFlushSize := f.bufWriter.Buffered()
	return unFlushSize + f.fileWriteCnt
}

func (f *FileBufIO) BufferRead() ([]byte, error) {
	//最简单的方式，直接从文件全读
	//finalPcmAcData, err := os.ReadFile(f.finalPcmPath)
	//return finalPcmAcData, err

	//用bufio方式
	return util.ReadLargeFileByBlock(f.finalPcmPath, ReadBufSize)
}

func (f *FileBufIO) BufferFile() (string, error) {
	return f.finalPcmPath, nil
}

func (f *FileBufIO) BufferClose() error {
	if f.file != nil {
		f.file.Close()
		//os.Remove(f.finalPcmPath)
		//目录也要删除
		log.InfoK(f.sId, "FileBufIO,BufferClose=%v", f.finalPcmDir)
		util.RemoveDir(f.finalPcmDir)
		f.file = nil
	}
	return nil
}

func (f *FileBufIO) flushAndSync() error {
	if f.bufWriter == nil {
		return nil
	}
	//把bufio内存剩余数据全部推入OS页缓存
	f.fileWriteCnt += f.bufWriter.Buffered()

	if err := f.bufWriter.Flush(); err != nil {
		return err
	}

	// OS页缓存强制写入物理磁盘，确保持久化,这里会导致大量io
	//if f.file != nil {
	//	return f.file.Sync()
	//}
	return nil
}

var cache *sysmem.MemInfoCache

func is2Disk(sId string, fileLen int) bool {
	if conf.App.DecisionType == 0 {
		// 1、通过时长并发判断要不要入磁盘
		if session.GetConnManager().GetDiskRw() < conf.App.DiskRwMax {
			Buffer2DiskThreshold = conf.App.Buffer2DiskMinutes * Buffer2DiskMinuteThreshold
			if fileLen > Buffer2DiskThreshold {
				//设置落盘标识
				session.GetConnManager().ConnDiskRw(sId)
				return true
			}
		}
		return false
	}

	// 1、每隔 x 秒过期刷新
	if cache == nil {
		cache = sysmem.NewMemInfoCache(MemCacheUpdateSec)
	}
	info, _ := cache.Get()

	// 2、每次获取新内存
	// info, _ := sysmem.GetHostMemInfo()
	if info.UsedPercent() > float64(conf.App.MemUsedPercent) {
		return true
	}
	return false
}
