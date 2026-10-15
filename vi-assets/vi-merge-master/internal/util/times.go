package util

import (
	"strconv"
	"time"
)

func PathTime(timeStr string) (string, string, error) {
	layoutInput := "2006-01-02 15:04:05"
	t, err := time.ParseInLocation(layoutInput, timeStr, time.Local)
	if err != nil {

		return "", "", err
	}
	dateOnly := t.Format("2006-01-02")
	unixTimestamp := t.Unix()
	return dateOnly, strconv.Itoa(int(unixTimestamp)), nil
}

func FileTime(timeStr string) (string, error) {
	t, err := time.ParseInLocation("2006-01-02 15:04:05", timeStr, time.Local)
	if err != nil {
		return "", err
	}

	return t.Format("20060102150405"), nil
}

func FileDate(timeStr string) (string, error) {
	t, err := time.ParseInLocation("2006-01-02 15:04:05", timeStr, time.Local)
	if err != nil {
		return "", err
	}

	return t.Format("20060102"), nil
}
