package bparser

import (
	"fmt"
	"net/url"
	"offline-merge/conf"
	"offline-merge/internal/models"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/pkg/registry"
)

type DccParser struct {
	//mode string
}

func (d *DccParser) NextNode(sId, curNode string) (string, error) {
	return registry.MediaNextNode(sId, curNode)
}

func (d *DccParser) UploadUri(sId, path, volume, fileName string) string {
	urlBody := conf.App.Outbound.MediaUri
	urlTail := fmt.Sprintf("%v/%v?path=%v&volume=%v", urlBody, fileName, url.QueryEscape(path), url.QueryEscape(volume))
	log.InfoK(sId, "DccParser,UploadUri,urlTail=%v", urlTail)
	return urlTail
}

func (d *DccParser) StoreNode(sId, srcAddress, curNode string) (string, error) {
	if len(srcAddress) > 10 {
		return srcAddress, nil
	}

	return registry.MediaNextNode(sId, "")
}

func (d *DccParser) FullMerge() bool {
	return true
}

func (d *DccParser) BizMode() string {
	return models.BizMerge
	//return d.mode
}

func (d *DccParser) CallOutName(sId, callerNo, calleeNo, startTime, suffix string) string {
	log.InfoK(sId, "CallOutName callerNo=%v,calleeNo=%v,startTime=%v ", callerNo, callerNo, startTime)
	return fmt.Sprintf("%v_%v.%v", sId, startTime, suffix)
}

func (d *DccParser) AudioChannels(source int) int {
	return 2
}

func NewDccParser(sId string) BizParser {
	return &DccParser{}
}
