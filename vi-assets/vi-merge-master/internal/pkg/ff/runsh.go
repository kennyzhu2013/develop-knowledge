package ff

import (
	"bytes"
	"context"
	"os/exec"
	"strconv"
	"time"
	"vi-merge/internal/pkg/log"
	"vi-merge/internal/util"
)

const (
	MinLength = 100

	LimitTime = "5"

	TIME_FLAG = "time="

	IS_QUIET_CMD = true
)

/*
*
FfmpegPath = "D:\\ProgramFiles\\ffmpeg-20200315-c467328-win64-static\\bin\\ffmpeg.exe"
*/
var (
	ffmpegPath = "ffmpeg" //"ffmpeg"
	levelQuiet = []string{"-loglevel", "quiet"}
)

const (
	ShSuc     = 1
	ShFail    = -1
	ShMinSize = -2
)

func SetFFPath(p string) {
	log.InfoF("-->set ffmpeg path %v", p)
	ffmpegPath = p
	if len(ffmpegPath) < 1 {
		log.WarnF("ffmpeg path is empty")
		ffmpegPath = "ffmpeg"
	}
}

func cmdRun(tag string, timeOut int, bizFlg string, args []string, output string) int {
	outStr, errStr, err := exeCmd(timeOut, args...)
	if len(outStr) > 0 {
		log.InfoK(tag, "cmdRun,%v,cmd,outStr=%v", bizFlg, outStr)
	}
	if len(errStr) > 0 {
		log.InfoK(tag, "cmdRun,%v,cmd,errStr=%v", bizFlg, errStr)
	}
	if err != nil {
		log.WarnK(tag, "cmdRun,%v,err=%v", bizFlg, errStr)
		return ShFail
	}
	return shSuc(output)
}

func exeCmd(timeOut int, args ...string) (string, string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeOut)*time.Millisecond)
	defer cancel()

	cmd := exec.CommandContext(ctx, ffmpegPath, args...)
	// cmd := exec.Command(ffmpegPath, args...)
	var out bytes.Buffer
	var errOut bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errOut
	err := cmd.Run()
	return out.String(), errOut.String(), err
}

func getWaitTimes(waitTime string) []string {
	if len(waitTime) < 1 {
		return nil
	}
	return []string{"-timelimit", waitTime}
}

func shSuc(output string) int {
	outLength, _ := util.FileLength(output)
	if outLength > MinLength {
		return ShSuc
	}
	return ShMinSize
}

func getPcmConfig(format string, sample int) []string {
	return []string{"-f", format, "-ac", strconv.Itoa(sample), "-ac", "1"}
}

func GetWaitTimeMs(size int) int {
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
