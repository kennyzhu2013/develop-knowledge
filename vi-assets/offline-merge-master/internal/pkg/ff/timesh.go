package ff

import (
	"bytes"
	"context"
	"offline-merge/internal/metrics"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/util"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

func GetTimeSh(id string, inPath string, inCode int) float32 {
	code, t := timeShRun(id, FfmpegPath, inPath, inCode)
	if code == ShSuc {
		return t
	} else if code == ShPathFail {
		log.WarnK(id, "GetTimeSh ShPathFail,inPath=%v,retry", inPath)
		_, t = timeShRun(id, FfmpegDefault, inPath, inCode)
		return t
	}
	return t
}

func timeShRun(id string, fPath string, inPath string, inCode int) (int, float32) {
	codec := getDecoderCodec(inCode)
	args := []string{"-hide_banner", "-timelimit", strconv.Itoa(LimitTime)}
	tail := []string{"-i", inPath, "-f", "null", "-"}

	if len(codec) < 1 {
		args = append(args, tail...)
	} else {
		args = append(args, codec...)
		args = append(args, tail...)
	}

	// cmd := exec.Command(FfmpegPath, args...)
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(LimitTime)*time.Millisecond)
	defer cancel()
	cmd := exec.CommandContext(ctx, fPath, args...)
	var out bytes.Buffer
	var errOut bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errOut
	err := cmd.Run()
	if err != nil {
		log.WarnK(id, "GetTimeSh run failed with %s,inPath=%v,Stdout=%v,Stderr=%v", err, inPath, out.String(), errOut.String())
		metrics.FfRunFailInc("offline")
		if strings.Contains(err.Error(), PathWrong) {
			return ShPathFail, 0
		}
		return ShFail, 0
	}

	//根据需要打印
	//log.WarnK(id, "GetTimeSh run out=%v", out.String())
	//log.WarnK(id, "GetTimeSh run errOut=%v", errOut.String())
	return ShSuc, getTimeByOutContent(id, errOut.String())
}

func DurationCal(sId, outPath string, bitrate int) float32 {
	time := durationByStream(outPath, bitrate)
	intTime := int(time)
	tailTime := time - float32(intTime)

	if intTime <= 0 {
		//mp3 的空文件，8k对应00:00:00.29, 16k对应00:00:00.18,
		if strings.HasSuffix(outPath, ".mp3") && tailTime < float32(0.3) {
			return 0
		}
		//wav 的空文件，8k、16k均对应00:00:00.10
		if strings.HasSuffix(outPath, ".wav") && tailTime < float32(0.11) {
			return 0
		}
	}

	if tailTime < 0.12 && intTime > 0 {
		//标准化的mp3，拿到的这两个时间是一致的
		return GetTimeSh(sId, outPath, -1)
	}
	return time
}

func durationByStream(path string, bitrate int) float32 {
	size, _ := util.FileLength(path)
	//16000比特/8=2000字节;
	secSize := float32(bitrate) / float32(8.0)
	if strings.HasSuffix(path, ".mp3") {
		size = size - 225
	} else if strings.HasSuffix(path, ".wav") {
		size = size - 58
	}
	if size < 0 {
		size = 0
	}
	return float32(size) / secSize
}

func getTimeByOutContent(tag string, content string) float32 {
	if content == "" {
		return 0
	}

	index := strings.LastIndex(content, TIME_FLAG)
	var newContent string
	if index >= 0 && len(content) > index+17 {
		newContent = content[index : index+17]
		newContent = strings.ReplaceAll(newContent, TIME_FLAG, "")
	} else {
		log.WarnK(tag, "GetTimeSh,getTimeByOutContent,getTimeByContent empty")
		return 0
	}

	info := strings.Split(newContent, ",")
	durationTime := ""
	if len(info) >= 1 {
		durationTime = strings.ReplaceAll(info[0], " ", "")
	}

	timeArr := strings.FieldsFunc(durationTime, func(c rune) bool {
		return c == '.' || c == ':'
	})

	if len(timeArr) >= 4 {
		h, _ := strconv.Atoi(timeArr[0])
		m, _ := strconv.Atoi(timeArr[1])
		sec, _ := strconv.Atoi(timeArr[2])
		millSec, _ := strconv.Atoi(timeArr[3])
		allSec := float32(h*3600 + m*60 + sec)
		allSec += float32(millSec) / 100.0
		return allSec
	}
	return 0
}
