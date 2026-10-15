package processor

import (
	"errors"
	"fmt"
	"math/rand"
	"path/filepath"
	"sync"
	"vi-merge/conf"
	"vi-merge/internal/httpinterface"
	"vi-merge/internal/metrics"
	"vi-merge/internal/models"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/pkg/registry"
	"vi-merge/internal/pkg/wavbox"
	"vi-merge/internal/util"
)

const (
	ZoneC = "ZoneC"
	ZoneM = "ZoneM"
)

type FileStore struct {
	SessionId string

	//文件节点，同一个会话优先走同一个节点，一个节点失败则切换下一个节点。
	fileNode string

	fileBuffer FileBuffer

	volume string

	fileServer *httpinterface.FileServer

	mu sync.RWMutex

	//记录执行了几次，只要一次成功，后续应该不需要了
	byte2FileSucCnt int

	resultMp3 bool
}

func NewFileStore() *FileStore {
	return &FileStore{
		fileNode:        "",
		volume:          "",
		byte2FileSucCnt: 0,
		resultMp3:       conf.App.FinalSuffixMp3,
	}
}

func (f *FileStore) Init(sessionId string, date string) {
	f.SessionId = sessionId
	f.fileServer = httpinterface.NewFileServer(sessionId)
	f.fileBuffer = NewFileBuffer(ByteBufferType, sessionId, date)
}

func (f *FileStore) SelectNodeUpload(wavAc2Data []byte, path string, fileName string, finalMerge bool) (string, error) {
	// 默认用上一次node
	fNode := f.fileNode
	var err error
	if len(fNode) < 1 {
		fNode, err = f.nextNode()
		if err != nil {
			return "", err
		}
	}

	// uploadName := fmt.Sprintf("%v/%v", path, wavName)
	err = f.nodeUpload(f.SessionId, wavAc2Data, finalMerge, path, f.volume, fileName, fNode)
	if err == nil {
		return fNode, nil
	} else if errors.Is(err, NodeAccessError) {
		fNode, _ = f.nextNode()
		// 切换节点成功，再次上传
		if f.nodeUpload(f.SessionId, wavAc2Data, finalMerge, path, f.volume, fileName, fNode) == nil {
			return fNode, nil
		}
	}
	metrics.UploadFailInc(models.MergeWav)
	return fNode, errors.New("upload fail")
}

func (f *FileStore) finalFullAudio(wavFinalName string, fmtParams wavbox.FmtParams, mergeWavUpload func(wavbox.FmtParams, []byte, string, string, bool) (string, error, bool)) (string, bool, error) {
	err := f.fileBufferFlush()
	if err != nil {
		log.WarnK(f.SessionId, "WavAc2Processor,file.Sync,err=%v", err)
		return "", false, err
	}

	var finalPcmAcData []byte
	path, _ := f.getLocalFile()
	log.DebugK(f.SessionId, "WavAc2Processor,final,path=%v,resultMp3=%v", path, f.resultMp3)
	if len(path) < 1 || !f.resultMp3 {
		finalPcmAcData, err = f.readLocalFile()
		path = ""
	}

	if err != nil {
		log.WarnK(f.SessionId, "WavAc2Processor,finalPcmPath,readFile,err=%v", err)
		return "", false, err
	}

	node, err, isMp3 := mergeWavUpload(fmtParams, finalPcmAcData, path, wavFinalName, true)
	if err != nil {
		log.WarnK(f.SessionId, "WavAc2Processor,mergeWavUpload,FinalName=%v,resultMp3=%v,isMp3=%v,err=%v", wavFinalName, f.resultMp3, isMp3, err)
	} else {
		log.InfoK(f.SessionId, "WavAc2Processor,mergeWavUpload,FinalName=%v,resultMp3=%v,isMp3=%v,Suc", wavFinalName, f.resultMp3, isMp3)
	}
	return node, isMp3, err
}

func (f *FileStore) fileBufferFlush() error {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.fileBuffer.BufferFlush()
}

func (f *FileStore) readLocalFile() ([]byte, error) {
	f.mu.RLock()
	defer f.mu.RUnlock()
	return f.fileBuffer.BufferRead()
}

func (f *FileStore) getLocalFile() (string, error) {
	f.mu.RLock()
	defer f.mu.RUnlock()
	return f.fileBuffer.BufferFile()
}

func (f *FileStore) recordPcm(pcms []byte) {
	f.mu.Lock()
	defer f.mu.Unlock()

	err := f.fileBuffer.Create()
	if err != nil {
		log.WarnK(f.SessionId, "WsPcmProcessor,fileBuffer Create,err=%v", err)
		return
	}
	err, full := f.fileBuffer.BufferWrite(pcms)

	if full {
		metrics.Buffer2FileInc(models.MergeWav)
		fileBuffer2, err := ByteBuffer2FileBuffer(f.SessionId, f.fileBuffer)
		if err != nil {
			if f.byte2FileSucCnt < 2 {
				log.WarnK(f.SessionId, "WsPcmProcessor,ByteBuffer2FileBuffer,err=%v", err)
			}
		} else {
			f.byte2FileSucCnt++
			log.InfoK(f.SessionId, "WsPcmProcessor,ByteBuffer2FileBuffer,Suc")
			f.fileBuffer = fileBuffer2
		}
	}
	if err != nil {
		log.WarnK(f.SessionId, "WsPcmProcessor,fileBuffer BufferWrite,err=%v", err)
		return
	}
	return
}

func (f *FileStore) saveDisk(wavAc2Data []byte, path string, wavName string) (string, error) {
	wavFilePath := fmt.Sprintf("%v/%v/%v", localRoot, path, wavName)
	dir := filepath.Dir(wavFilePath)
	err := util.CreateDirIfNotExist(dir)
	if err != nil {
		log.WarnK(f.SessionId, "WavAc2Processor,saveDisk, wavFilePath: %v, err: %v", wavFilePath, err)
		return "", err
	}

	//err = os.WriteFile(wavFilePath, wavAc2Data, 0644)
	err = util.WriteFileLowIO(wavFilePath, wavAc2Data, 0644)
	if err != nil {
		log.WarnK(f.SessionId, "WavAc2Processor,saveDisk,WriteFile=%v,err=%v", wavFilePath, err)
	}
	log.InfoK(f.SessionId, "WavAc2Processor,saveDisk Suc,WriteFile=%v", wavFilePath)
	metrics.SaveLocalDiskInc(models.MergeWav)
	return wavFilePath, err
}

func (f *FileStore) nextNode() (string, error) {
	nodeCnt := 0
	for nodeCnt < 3 {
		destNode, err := registry.GetInnerNode(conf.App.Outbound.Cluster, conf.App.Outbound.AppName)
		if err != nil {
			log.WarnK(f.SessionId, "WavAc2Processor,nextNode err,err=%v", err)
			metrics.FileNodeNotFoundInc(models.MergeWav)
			return "", err
		}
		nodeCnt++
		//选择不一样的节点
		if destNode != f.fileNode {
			log.DebugK(f.SessionId, "WavAc2Processor,nextNode=%v", destNode)
			f.fileNode = destNode
			return destNode, nil
		} else if nodeCnt == 3 {
			log.WarnK(f.SessionId, "WavAc2Processor,nextNode,last choice,node=%v", destNode)
			//最后一次不强调跟前一次不一样
			return destNode, nil
		}
	}
	log.WarnK(f.SessionId, "WavAc2Processor,nextNode fail")
	return "", NodeEmptyErr
}

func (f *FileStore) nodeUpload(sessionId string, wavAc2Data []byte, fullMerge bool, pathPrams, volume, uploadName, fileNode string) error {
	// 上传到vi-mdown,这里需要用ETCD获取节点. 同一个callId尽量去同一个下载节点
	if len(fileNode) > 0 {
		url := fmt.Sprintf("http://%v%v", fileNode, conf.App.Outbound.Uri)

		var code int
		var fileResp *httpinterface.FileResp

		zone := ZoneC
		// ?zone=ZoneM 合音；?zone=ZoneC 分片
		if fullMerge {
			zone = ZoneM
		}

		//用mock接口
		//code, fileResp = f.fileServer.UploadTest(url, pathPrams, volume, uploadName, wavAc2Data)
		//旧接口
		//code, fileResp := f.fileServer.Upload(url, pathPrams, volume, uploadName, wavAc2Data)
		//新接口
		code, fileResp = f.fileServer.UploadMultipartFile(url, pathPrams, zone, volume, uploadName, wavAc2Data)

		//用mock接口
		//code, fileResp := f.fileServer.UploadTest(url, pathPrams, volume, uploadName, wavAc2Data)
		log.DebugK(sessionId, "FileStore,Upload,path=%v,volume=%v,uploadName=%v,code=%v", pathPrams, volume, uploadName, code)
		if code == httpinterface.FILE_SUC_CODE {
			if len(fileResp.Volume) > 3 {
				f.volume = fileResp.Volume
			}
			return nil
		} else if code == httpinterface.CONNECT_ERROR_5XX || code == httpinterface.CONNECT_ERROR_CODE {
			log.WarnK(sessionId, "WavAc2Processor,Upload Fail1,name=%v,code=%v", uploadName, code)
			return NodeAccessError
		}
	} else {
		log.WarnK(sessionId, "WavAc2Processor,Upload Fail1,name=%v,fileNode empty", uploadName)
	}
	return NodeEmptyErr
}

func (f *FileStore) cleanFullMergeFile() error {
	if f.fileBuffer != nil {
		return f.fileBuffer.BufferClose()
	}
	return nil
}

func (f *FileStore) Close() {
	f.cleanFullMergeFile()
}

func Hit10Percent() bool {
	// 生成 [0,99] 整数，仅 0 命中，概率10/100=10%
	n := rand.Intn(100)
	return n < 10
}
