package dtmf

import "errors"

var (
	ErrStatus    = errors.New("invalid status")
	ErrTimeout   = errors.New("detect timeout")
	ErrRtpHeader = errors.New("invalid rtp header")
)