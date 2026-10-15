package models

import "fmt"

const (
	BizMerge = "dccMerge"
	BizVi    = "voiceInspection"
)

type MergeCtx struct {
	MergeMessage MediaMerge

	MergeOptions MergeOptions

	LocalOptions *LocalOptions

	//本地存储目录,如果上传失败，就记录在本地
	LocalStoreDir string
}

// ------------------- start of LocalOptions --------------------------
type LocalOptions struct {
	// 拷贝到本地的左右腿地址
	LocalLegList []*PairFileLeg

	// 早期文件
	LocalEarlyList []*PairFileLeg

	// 通话结果文件目录
	CallOutItem *LocalResultItem

	// 主叫单腿文件
	CallerItem *LocalResultItem

	// 被叫单腿文件
	CalleeItem *LocalResultItem

	// 全链路录音(早期文件or拼接后文件)
	FullLinkItem *LocalResultItem

	// 视频merge
	VideoItem *LocalResultItem

	//结果文件是单声道还是双声道
	OutAc int
}

func (mc *MergeCtx) Mode() string {
	return mc.MergeOptions.Mode
}

func NewLocalOptions() *LocalOptions {
	return &LocalOptions{}
}

func (l *LocalOptions) ToString() string {
	return fmt.Sprintf(
		"LocalOptions{"+
			"localLegList=%v, "+
			"localEarlyList=%v, "+
			"callOutItem=%v, "+
			"callerItem=%v, "+
			"calleeItem=%v, "+
			"fullLinkItem=%v, "+
			"videoItem=%v"+
			"}",
		l.LocalLegList,
		l.LocalEarlyList,
		l.CallOutItem,
		l.CallerItem,
		l.CalleeItem,
		l.FullLinkItem,
		l.VideoItem,
	)
}

// ------------------- end of LocalOptions --------------------------

// ------------------- start of LocalResultItem --------------------------
type LocalResultItem struct {
	ResultName     string  // 文件结果名
	ResultPath     string  // 文件的本地路径
	FileLen        int64   // 文件长度 (Java long → Go int64)
	ResultDuration float32 // 录音时长 (Java float → Go float32)
	UploadMark     int     // 上传标记
	CallResultFile bool    // 通话的结果文件
}

func NewLocalResultItem() *LocalResultItem {
	return &LocalResultItem{
		UploadMark:     -1, // 默认值 -1
		CallResultFile: false,
	}
}

// ToString 等价 Java toString() 方法
func (l *LocalResultItem) ToString() string {
	return fmt.Sprintf(
		"LocalResultItem{"+
			"resultName='%v', "+
			"resultPath='%v', "+
			"fileLen=%d, "+
			"resultDuration=%f, "+
			"uploadMark=%d"+
			"}",
		l.ResultName,
		l.ResultPath,
		l.FileLen,
		l.ResultDuration,
		l.UploadMark,
	)
}

//------------------- end of LocalResultItem --------------------------

// ------------------- start of PairFileLeg --------------------------
type PairFileLeg struct {
	//文件结果名
	ResultName string

	//文件的本地路径
	ResultPath string

	FileLen uint64

	//文件录音时长
	ResultDuration float32

	//上传标记
	UploadMark int
	CallerLeg  *FileLeg // 主叫腿
	CalleeLeg  *FileLeg // 被叫腿
}

func NewPairFileLeg() *PairFileLeg {
	return &PairFileLeg{}
}

func NewPairFileLegWithArgs(callerLeg, calleeLeg *FileLeg) *PairFileLeg {
	return &PairFileLeg{
		CallerLeg:  callerLeg,
		CalleeLeg:  calleeLeg,
		UploadMark: -1,
	}
}

func (p *PairFileLeg) ToString() string {
	return fmt.Sprintf(
		"PairFileLeg{"+
			"callerLeg=%v, "+
			"calleeLeg=%v"+
			"}",
		p.CallerLeg,
		p.CalleeLeg,
	)
}

//------------------- end of PairFileLeg --------------------------

// ------------------- start of FileLeg --------------------------
type FileLeg struct {
	FileName   string  // 文件名
	InCode     int     // 编码，默认 -1
	LocalPath  string  // 本地路径
	FileLength int64   // 文件长度，默认 0
	Duration   float32 // 时长，默认 0
	PcmAttr    PcmAttr // pcm 属性
}

func NewFileLeg() *FileLeg {
	return &FileLeg{
		InCode:     -1,  // 默认值
		FileLength: 0,   // 默认值
		Duration:   0.0, // 默认值
	}
}

func NewFileLegWithArgs(name string, localPath string, pcmAtt PcmAttr) *FileLeg {
	return &FileLeg{
		FileName:   name,
		LocalPath:  localPath,
		PcmAttr:    pcmAtt,
		InCode:     -1,
		FileLength: 0,
		Duration:   0.0,
	}
}

// ToString 等价 Java toString()
func (f *FileLeg) ToString() string {
	return fmt.Sprintf(
		"FileLeg{"+
			"fileName='%v', "+
			"inCode=%d, "+
			"localPath='%v', "+
			"fileLength=%d, "+
			"duration=%f, "+
			"pcmAttr=%v"+
			"}",
		f.FileName,
		f.InCode,
		f.LocalPath,
		f.FileLength,
		f.Duration,
		f.PcmAttr,
	)
}

//------------------- end of FileLeg --------------------------

// ------------------- start of FileLeg --------------------------
type PcmAttr struct {
	Format string // 对应 format
	Sample int    // 对应 sample
	Ac     int    // 对应 ac，默认 1
}

func NewPcmAttr() *PcmAttr {
	return &PcmAttr{
		Ac: 1,
	}
}

func NewPcmAttrWithArgs(f string, sample int) *PcmAttr {
	return &PcmAttr{
		Format: f,
		Sample: sample,
		Ac:     1,
	}
}

func (p *PcmAttr) ToString() string {
	return fmt.Sprintf(
		"PcmAttr{"+
			"format='%v', "+
			"sample=%d, "+
			"ac=%d"+
			"}",
		p.Format,
		p.Sample,
		p.Ac,
	)
}

//------------------- end of FileLeg --------------------------
