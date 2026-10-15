package models

type MergeChan struct {
	LeftPcm  []byte
	RightPcm []byte

	//start end ...
	Event string
}
