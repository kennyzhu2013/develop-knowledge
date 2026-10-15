package models

const (
	FILE_NAME = "fileName"
)

type MergeCfg struct {
	//是否声道反转
	AcReverse bool

	//声道数量，只要针对左右腿的生成结果录音
	Ac int

	//编码值
	Codec string

	//采样率
	Ar int

	//输出的比特率 bps

	Ab int

	//输入端的音频的格式
	LeftInCode  int
	RightInCode int
}

func (m *MergeCfg) IsAcOne() bool {
	return m.Ac == 1
}
