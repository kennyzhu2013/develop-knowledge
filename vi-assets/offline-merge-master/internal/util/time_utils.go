package util

import (
	"strconv"
	"time"
)

const OFF_HOUR = 8

func Timestamp10ToYmdHis(ts int64) string {
	zone := time.FixedZone("CST", OFF_HOUR*3600)
	t := time.Unix(ts, 0).In(zone)
	return t.Format("20060102150405")
}

func TimestampStr10ToYmdHis(tsStr string) string {
	//1735689600  -->  20250101000000
	ts, err := strconv.ParseInt(tsStr, 10, 64)
	if err != nil {
		return ""
	}
	zone := time.FixedZone("CST", OFF_HOUR*3600)
	return time.Unix(ts, 0).In(zone).Format("20060102150405")
}

func Timestamp13ToYmdHis(ms int64) string {
	zone := time.FixedZone("UTC+8", OFF_HOUR*3600)
	// 13位是毫秒，用 UnixMilli 解析
	t := time.UnixMilli(ms).In(zone)
	// Go 固定格式化字符串
	return t.Format("20060102150405")
}

func Timestamp13StrToYmdHis(msStr string) string {
	//1744099200000  -->  20250408160000
	ms, err := strconv.ParseInt(msStr, 10, 64)
	if err != nil {
		return ""
	}
	return Timestamp13ToYmdHis(ms)
}
