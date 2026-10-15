package models

import "errors"

var (
	MqMessageInvalid = errors.New("message body not json")

	NodeEmptyErr    = errors.New("node empty err")
	NodeAccessError = errors.New("node access err")
	NodeUploadErr   = errors.New("node upload err")
	FileSaveLocal   = errors.New("file save local err")
)
