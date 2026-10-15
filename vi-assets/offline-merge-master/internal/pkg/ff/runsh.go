package ff

import (
	"bytes"
	"context"
	"offline-merge/internal/audioformat"
	"offline-merge/internal/metrics"
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/util"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

const (
	MinLength = 100

	LimitTime = 5000

	TIME_FLAG = "time="

	IS_QUIET_CMD = true

	PathWrong = "executable file not found"

	FfmpegDefault = "ffmpeg"
)

/*
*
FfmpegPath = "D:\\ProgramFiles\\ffmpeg-20200315-c467328-win64-static\\bin\\ffmpeg.exe"
*/
var (
	FfmpegPath = "/home/production/ffmpeg-4.1.3-amd64-static/ffmpeg" //"ffmpeg"

	levelQuiet = []string{"-loglevel", "quiet"}

	// 20260828 add
	isTimeLimit = true
)

// todo:add more
const (
	ShSuc      = 1
	ShFail     = -1
	ShMinSize  = -2
	ShPathFail = -3
)

func SetFFPath(p string) {
	log.InfoF("-->set ffmpeg path %v", p)
	FfmpegPath = p
	if len(FfmpegPath) < 1 {
		log.WarnF("ffmpeg path is empty")
		FfmpegPath = "ffmpeg"
	}
}

func GetStringCode(code int) string {
	switch code {
	case ShSuc:
		return "success"

	case ShFail:
		return "general error"

	case ShMinSize:
		return "invalid output file size"

	case ShPathFail:
		return "invalid ffmpeg bin path"
	}

	return "unknown error"
}

func ConcatAudio(tag, fileList, output string, waitTime int, format string) int {
	//这里不加"-ac 1"是因为可能会双声道合并
	//windows 不支持用timelimit,会报错，但是不影响执行"errStr=-timelimit not implemented on this OS"
	args := []string{"-hide_banner"}
	tail := []string{"-f", "concat", "-safe", "0", "-i", fileList, "-c", "copy", "-y", "-f", format, output}

	if IS_QUIET_CMD {
		args = append(args, levelQuiet...)
	}
	waitArgs := getWaitTimes(waitTime)
	if len(waitArgs) > 1 {
		args = append(args, waitArgs...)
	}
	args = append(args, tail...)

	return cmdRun(tag, waitTime, "ConcatAudio", args, output)
}

func cmdRun(tag string, timeOut int, bizFlg string, args []string, output string) int {
	code := cmdRunOne(tag, FfmpegPath, timeOut, bizFlg, args, output)
	if code == ShPathFail {
		log.WarnK(tag, "cmdRun ShPathFail,output=%v,retry", output)
		return cmdRunOne(tag, FfmpegDefault, timeOut, bizFlg, args, output)
	}
	return code
}

func cmdRunOne(tag string, fPath string, timeOut int, bizFlg string, args []string, output string) int {
	outStr, errStr, err := exeCmd(fPath, timeOut, args...)
	if len(outStr) > 1 {
		log.InfoK(tag, "cmdRun,%v,cmd,outStr=%v", bizFlg, outStr)
	}
	if len(errStr) > 1 {
		log.InfoK(tag, "cmdRun,%v,cmd,errStr=%v", bizFlg, errStr)
	}
	if err != nil {
		log.ErrorK(tag, "cmdRun,%v,err=%v,args=[%v]", bizFlg, err, args)
		if strings.Contains(err.Error(), PathWrong) {
			return ShPathFail
		}
		metrics.FfRunFailInc("offline")
		return ShFail
	}
	return shSuc(output)
}

//func exeCmdNoTimeOut(args ...string) (string, string, error) {
//	cmd := exec.Command(FfmpegPath, args...)
//	var out bytes.Buffer
//	var errOut bytes.Buffer
//	cmd.Stdout = &out
//	cmd.Stderr = &errOut
//	err := cmd.Run()
//	return out.String(), errOut.String(), err
//}

func exeCmd(fPath string, timeOut int, args ...string) (string, string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeOut)*time.Millisecond)
	defer cancel()

	cmd := exec.CommandContext(ctx, fPath, args...)
	// cmd := exec.Command(ffmpegPath, args...)
	var out bytes.Buffer
	var errOut bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errOut
	err := cmd.Run()
	return out.String(), errOut.String(), err
}

func shSuc(output string) int {
	outLength, _ := util.FileLength(output)
	if outLength > MinLength {
		return ShSuc
	}
	return ShMinSize
}

func getDecoderCodec(inCode int) []string {
	switch inCode {
	case audioformat.AMR_NB.GetCode():
		return []string{"-acodec", "libopencore_amrnb"}
	case audioformat.AMR_WB.GetCode():
		return []string{"-acodec", "libopencore_amrwb"}
	case audioformat.G729.GetCode():
		return []string{"-acodec", "g729"}
	case audioformat.PCM_S16_8K.GetCode():
		return getPcmConfig("S16LE", 8000)
	case audioformat.PCM_S16_16K.GetCode():
		return getPcmConfig("S16LE", 16000)
	case audioformat.PCM_F32_8K.GetCode():
		return getPcmConfig("F32LE", 8000)
	case audioformat.PCM_F32_16K.GetCode():
		return getPcmConfig("F32LE", 16000)
	default:
		return []string{}
	}
}

func getPcmConfig(format string, sample int) []string {
	return []string{"-f", format, "-ar", strconv.Itoa(sample), "-ac", "1"}
}

func getWaitTimes(waitTime int) []string {
	if !isTimeLimit {
		return nil
	}
	if waitTime < 1 {
		return nil
	}
	unitTime := "" //"ms"
	return []string{"-timelimit", strconv.Itoa(waitTime) + unitTime}
}

//func GetWaitTimeBySize(size int64) string {
//	t := GetWaitTimeMs(size)
//	return fmt.Sprintf("%vms", t)
//}

func GetWaitTimeMs(size int64) int {
	if size > 10*1024*1024 {
		return 160 * 1000
	} else if size > 5*1024*1024 {
		return 100 * 1000
	} else if size > 1*1024*1024 {
		return 80 * 1000
	} else if size > 512*1024 {
		return 50 * 1000
	} else if size > 100*1024 {
		return 20 * 1000
	} else if size > 50*1024 {
		return 10 * 1000
	}
	return 5 * 1000
}
