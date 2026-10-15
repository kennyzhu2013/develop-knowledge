package registry

import (
	"offline-merge/conf"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
)

func UploadNextNode(sId, curNode string) (string, error) {
	nodeCnt := 0
	for nodeCnt < 3 {
		destNode, err := GetInnerNode(conf.App.Outbound.FileCluster, conf.App.Outbound.FileAppName)
		if err != nil {
			log.WarnK(sId, "FileStore,nextNode err,err=%v", err)
			return "", err
		}
		nodeCnt++
		log.DebugK(sId, "nextNode=%v", destNode)

		//选择不一样的节点
		if destNode != curNode {
			log.DebugK(sId, "FileStore,nextNode=%v", destNode)
			return destNode, nil
		} else if nodeCnt == 3 {
			log.WarnK(sId, "WavAc2Processor,nextNode,last choice,node=%v", destNode)
			//最后一次不强调跟前一次不一样
			return destNode, nil
		}
	}
	return "", models.NodeEmptyErr
}

func MediaNextNode(sId, curNode string) (string, error) {
	nodeCnt := 0
	for nodeCnt < 3 {
		destNode, err := GetNode(conf.App.Outbound.MediaCluster, conf.App.Outbound.MediaAppName)
		if err != nil {
			log.WarnK(sId, "FileStore,nextNode err,err=%v", err)
			return "", err
		}
		nodeCnt++
		if destNode != curNode {
			return destNode, nil
		}
	}
	return "", models.NodeEmptyErr
}
