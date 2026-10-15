package bparser

import (
	"fmt"
	"offline-merge/conf"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/pkg/registry"
)

type ViParser struct {
}

func (v *ViParser) NextNode(sId, curNode string) (string, error) {
	return registry.UploadNextNode(sId, curNode)
}

func (v *ViParser) UploadUri(sId, path, volume, fileName string) string {
	urlBody := conf.App.Outbound.FileUri
	//urlTail := fmt.Sprintf("path=%v&volume=%v", url.QueryEscape(path), url.QueryEscape(volume))
	//UploadUri := fmt.Sprintf("%v/%v?%v", urlBody, fileName, urlTail)
	UploadUri := fmt.Sprintf("%v/%v?path=%v&volume=%v", urlBody, fileName, path, volume)
	log.InfoK(sId, "ViParser,UploadUri,UploadUri=%v", UploadUri)
	return UploadUri
}

func (v *ViParser) StoreNode(sId string, srcAddress string, curNode string) (string, error) {
	return registry.UploadNextNode(sId, curNode)
}

func (v *ViParser) FullMerge() bool {
	return false
}

func (v *ViParser) BizMode() string {
	return models.BizVi
}

func (v *ViParser) CallOutName(sId, callerNo, calleeNo, startTime, suffix string) string {
	log.InfoK(sId, "CallOutName callerNo=%v,calleeNo=%v,startTime=%v ", callerNo, callerNo, startTime)
	return fmt.Sprintf("%v_%v.%v", sId, startTime, suffix)
}

func (v *ViParser) AudioChannels(source int) int {
	if source == 1 || source == 2 {
		return 1
	}
	return 2
}

func NewViParser(sId string) BizParser {
	return &ViParser{}
}
