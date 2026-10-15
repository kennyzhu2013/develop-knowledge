package bparser

type BizParser interface {
	BizMode() string

	// FullMerge 是否需要前链路录音
	FullMerge() bool

	// CallOutName 结果文件名
	CallOutName(sId, callerNo, calleeNo, startTime, suffix string) string

	// AudioChannels 声道数量
	AudioChannels(source int) int

	// StoreNode 存储节点
	StoreNode(sId, srcAddress, curNode string) (string, error)

	NextNode(sId, curNode string) (string, error)

	UploadUri(sId, path, volume, fileName string) string
}
