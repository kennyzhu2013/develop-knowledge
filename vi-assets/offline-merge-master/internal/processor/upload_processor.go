package processor

import (
	"errors"
	"fmt"
	"offline-merge/conf"
	"offline-merge/internal/httpinterface"
	"offline-merge/internal/metrics"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/processor/bparser"
	"offline-merge/internal/util"
	"path/filepath"
	"sync"
	"time"
)

var (
	localRoot = "/data/offlocal"

	UploadSuc  = 1
	UploadFail = -1
)

type FileStore struct {
	sessionId string

	fileNode string

	//节点
	volume string

	//media的节点
	srcAddress string

	fileServer *httpinterface.FileServer

	wg sync.WaitGroup

	//上传失败数量
	uploadFailCnt int
}

func NewFileStore() *FileStore {
	return &FileStore{
		//fileNode:      "",
		volume:        "",
		uploadFailCnt: 0,
	}
}

func (f *FileStore) Init(sessionId, volume string, address string) {
	f.sessionId = sessionId
	f.volume = volume
	f.srcAddress = address
	f.fileServer = httpinterface.NewFileServer(sessionId)
}

func (f *FileStore) SaveFile(mergeCtx *models.MergeCtx, parser bparser.BizParser) (int, int) {
	localResultItems := f.localResultItems(mergeCtx)
	uploadSum := len(localResultItems)

	path := mergeCtx.MergeMessage.Path
	startTime := time.Now()
	log.InfoK(f.sessionId, "fileUpload,localResultItems,size=%v", uploadSum)
	if uploadSum > 1 {
		f.parallelUpload(path, localResultItems, parser)
	} else if uploadSum == 1 {
		f.oneFileSave(path, localResultItems[0], parser, false)
	}

	spTime := time.Since(startTime).Milliseconds()
	if spTime > 1000 {
		log.WarnK(f.sessionId, "fileUpload,long,spTime=%v", spTime)
	} else {
		log.InfoK(f.sessionId, "fileUpload,spTime=%v", spTime)
	}

	metrics.MergeUploadTimeObserve(mergeCtx.Mode(), float64(spTime))
	return uploadSum, f.uploadFailCnt
}

func (f *FileStore) parallelUpload(path string, localResultItems []*models.LocalResultItem, parser bparser.BizParser) {
	for _, localItem := range localResultItems {
		f.wg.Add(1)
		go f.oneFileSave(path, localItem, parser, true)
	}
	f.wg.Wait()
}

func (f *FileStore) oneFileSave(path string, localResultItem *models.LocalResultItem, parser bparser.BizParser, wgAdd bool) error {
	defer func() {
		if wgAdd {
			f.wg.Done()
		}
		if v := recover(); v != nil {
			log.WarnK(f.sessionId, "FileStore,oneFileSave,file=%v,defer,%v", localResultItem.ResultName, v)
		}
	}()
	log.InfoK(f.sessionId, "FileStore,oneFileSave,file=%v", localResultItem.ResultName)
	//旧实现调用
	//fileBytes, err := os.ReadFile(localResultItem.ResultPath)
	//err = f.selectNodeUpload(fileBytes, path, localResultItem.ResultName, parser)

	err := f.selectNodeFileUpload(localResultItem.ResultPath, path, localResultItem.ResultName, localResultItem.CallResultFile, parser)
	if err != nil {
		//metrics.UploadFailInc("offline", err.Error())
		metrics.UploadFailInc(parser.BizMode(), err.Error())
		log.WarnK(f.sessionId, "FileStore,selectNodeUpload,SaveLocal=%v,err=%v", conf.App.SaveLocal, err)
		localResultItem.UploadMark = UploadFail

		if conf.App.SaveLocal {
			// 存磁盘/nfs,存盘当失败？
			var audioFilePath string
			audioFilePath, err = f.saveDisk(localResultItem.ResultPath, localResultItem.ResultName)
			log.InfoK(f.sessionId, "FileStore,saveDisk,audioFilePath=%v,err=%v", audioFilePath, err)
			if err != nil {
				metrics.FileRwErrInc("off-local")
			} else {
				metrics.FileLocalSaveInc("off")
			}
			return models.FileSaveLocal
		}

		f.uploadFailCnt++
		log.InfoK(f.sessionId, "FileStore,saveDisk,err=%v", err)
		return models.NodeUploadErr
	}
	localResultItem.UploadMark = UploadSuc
	//log.InfoK(f.sessionId, "FileStore,selectNodeUpload,Suc,Name=%v", localResultItem.ResultName)
	return nil
}

func (f *FileStore) localResultItems(mergeCtx *models.MergeCtx) []*models.LocalResultItem {
	var uploadLocalResultItems []*models.LocalResultItem

	isSeparateEarly := mergeCtx.MergeOptions.SeparateEarly
	isFullOk := false
	fullLinkItem := mergeCtx.LocalOptions.FullLinkItem
	if f.isResultExist(fullLinkItem) {
		uploadLocalResultItems = append(uploadLocalResultItems, fullLinkItem)
		isFullOk = true
	}

	//全链路，并且不分离早期，全链路包含了通话，这时候就不需要单独通话录音
	if !isSeparateEarly && isFullOk {
		fullLinkItem.CallResultFile = true
		log.InfoK(f.sessionId, "isFullOk and not separateEarly")
	} else {
		callOutItem := mergeCtx.LocalOptions.CallOutItem
		callOutItem.CallResultFile = true
		if f.isResultExist(callOutItem) {
			uploadLocalResultItems = append(uploadLocalResultItems, callOutItem)
		}
	}

	callerItem := mergeCtx.LocalOptions.CallerItem
	if f.isResultExist(callerItem) {
		uploadLocalResultItems = append(uploadLocalResultItems, callerItem)
	}

	calleeItem := mergeCtx.LocalOptions.CalleeItem
	if f.isResultExist(calleeItem) {
		uploadLocalResultItems = append(uploadLocalResultItems, calleeItem)
	}
	return uploadLocalResultItems
}

func (f *FileStore) isResultExist(callOutItem *models.LocalResultItem) bool {
	if callOutItem != nil && util.IsFileExist(callOutItem.ResultPath) {
		return true
	}
	return false
}

//func (f *FileStore) selectNodeUploadOld(wavAc2Data []byte, path, outName string, parser bparser.BizParser) error {
//	fileNode, err := parser.StoreNode(f.sessionId, f.srcAddress, "")
//	//err := f.nextNode()
//	if err != nil {
//		return err
//	}
//	f.fileNode = fileNode
//
//	//uploadName := util.FilepathJoin(f.volume, wavName) //filepath.Join(f.volume, wavName)
//	err = f.nodeUploadOld(f.sessionId, wavAc2Data, path, f.volume, outName, fileNode, parser)
//	if err == nil {
//		return nil
//	} else if errors.Is(err, models.NodeAccessError) {
//		// 切换节点成功，再次上传
//		fileNode2 := f.nextNode(parser)
//		if len(fileNode2) > 2 {
//			fileNode = fileNode2
//			f.fileNode = fileNode
//		}
//
//		if f.nodeUploadOld(f.sessionId, wavAc2Data, path, f.volume, outName, fileNode, parser) == nil {
//			return nil
//		}
//	}
//	return errors.New("upload fail")
//}

func (f *FileStore) selectNodeFileUpload(filePath string, path, outName string, callRetFile bool, parser bparser.BizParser) error {
	//if len(f.fileNode) < 1 {
	fileNode, err := parser.StoreNode(f.sessionId, f.srcAddress, "")
	//err := f.nextNode()
	if err != nil {
		return err
	}
	f.fileNode = fileNode
	//}

	//uploadName := util.FilepathJoin(f.volume, wavName) //filepath.Join(f.volume, wavName)
	err = f.nodeFileUpload(f.sessionId, filePath, path, f.volume, outName, fileNode, callRetFile, parser)
	if err == nil {
		return nil
	} else if errors.Is(err, models.NodeAccessError) {
		// 切换节点成功，再次上传
		fileNode2 := f.nextNode(parser)
		if len(fileNode2) > 2 {
			fileNode = fileNode2
			f.fileNode = fileNode
		}

		log.InfoF("outName=%v,retry node=%v,upload", outName, fileNode)
		if err = f.nodeFileUpload(f.sessionId, filePath, path, f.volume, outName, fileNode, callRetFile, parser); err == nil {
			return nil
		}
		return err
	}
	return errors.New("upload fail")
}

func (f *FileStore) saveDisk(file string, wavName string) (string, error) {
	//存本地算成功还是失败？失败把
	wavFilePath := localRoot + "/" + wavName
	dir := filepath.Dir(wavFilePath)
	err := util.CreateDirIfNotExist(dir)
	if err != nil {
		log.WarnK(f.sessionId, "FileStore,saveDisk, wavFilePath: %v, err: %v", wavFilePath, err)
		return "", err
	}

	_, err = util.CopyFileFast(file, wavFilePath)
	//写文件/nfs
	//err = os.WriteFile(wavFilePath, wavAc2Data, 0644)
	if err != nil {
		log.WarnK(f.sessionId, "FileStore,saveDisk,WriteFile=%v,err=%v", wavFilePath, err)
	}
	log.InfoK(f.sessionId, "FileStore,saveDisk Suc,WriteFile=%v", wavFilePath)
	return wavFilePath, err
}

//func (f *FileStore) nodeUploadOld(sessionId string, wavAc2Data []byte, path, volume, fileName, uploadNode string, parser bparser.BizParser) error {
//	//上传到mdown,这里需要用ETCD获取节点. 同一个callId尽量去同一个下载节点
//	if len(uploadNode) > 0 {
//		urlStr := fmt.Sprintf("http://%v%v", uploadNode, parser.UploadUri(sessionId, path, volume, fileName))
//		code, fileResp := f.fileServer.Upload(urlStr, volume, fileName, wavAc2Data)
//		log.InfoK(sessionId, "FileStore,Upload,path=%v,volume=%v,fileName=%v,code=%v(0:suc)", path, volume, fileName, code)
//		if code == httpinterface.FILE_SUC_CODE {
//			if len(fileResp.Volume) > 3 {
//				f.volume = fileResp.Volume
//			}
//			return nil
//		} else if code == httpinterface.CONNECT_ERROR_5XX || code == httpinterface.CONNECT_ERROR_CODE {
//			return models.NodeAccessError
//		}
//	}
//	return models.NodeEmptyErr
//}

func (f *FileStore) nodeFileUpload(sessionId string, filePath string, path, volume, fileName, uploadNode string, callRetFile bool, parser bparser.BizParser) error {
	//上传到mdown,这里需要用ETCD获取节点. 同一个callId尽量去同一个下载节点
	if len(uploadNode) > 0 {
		urlStr := fmt.Sprintf("http://%v%v", uploadNode, parser.UploadUri(sessionId, path, volume, fileName))
		var code int
		var fileResp *httpinterface.FileResp
		if parser.BizMode() == models.BizVi {
			code, fileResp = f.fileServer.UploadFile(urlStr, volume, fileName, filePath)
		} else {
			// 700兼容
			bytes, err := util.ReadLargeFileByBlock(filePath, 1024*1024)
			if err != nil {
				log.WarnK(sessionId, "FileStore,fileUpload,ReadFile=%v,err=%v", filePath, err)
				return err
			}
			code, fileResp = f.fileServer.Upload(urlStr, volume, fileName, bytes)
		}

		log.InfoK(sessionId, "FileStore,Upload,path=%v,volume=%v,fileName=%v,code=%v,callRetFile=%v", path, volume, fileName, code, callRetFile)
		if code == httpinterface.FILE_SUC_CODE {
			// 判断如果f.volume为空，可以设置;如果不为空，则是结果文件才设置
			if len(f.volume) < 1 {
				f.volume = fileResp.Volume
			} else if len(fileResp.Volume) > 3 && callRetFile {
				f.volume = fileResp.Volume
			}
			return nil
		} else if code == httpinterface.CONNECT_ERROR_5XX || code == httpinterface.CONNECT_ERROR_CODE {
			return models.NodeAccessError
		}
	}
	return models.NodeEmptyErr
}

func (f *FileStore) FileNode() string {
	return f.fileNode
}

func (f *FileStore) GetVolume() string {
	return f.volume
}

func (f *FileStore) Close() {

}

func (f *FileStore) nextNode(parser bparser.BizParser) string {
	node, err := parser.NextNode(f.sessionId, "")
	if err != nil {
		log.WarnK(f.sessionId, "FileStore,nextNode,err=%v", err)
		return ""
	}

	return node
}
