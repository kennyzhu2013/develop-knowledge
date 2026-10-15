package dtmf

func GetTimestamp(time []byte) int64 {
	return int64(time[0])*256*256*256 + int64(time[1])*256*256 + int64(time[2])*256 + int64(time[3])
}