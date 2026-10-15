package ff

import (
	"offline-merge/internal/pkg/log"
	"offline-merge/internal/util"
	"strconv"
)

const (
	Libmp3lame = "libmp3lame"
)

func Mix2MonoLong(tag string, leftFileName, rightFileName, outFileName string, ar int, ab int, ac int, leftInCode int, rightInCode int, format string) int {
	waitTime, code := checkPairLegs(tag, leftFileName, rightFileName)
	if code == ShFail {
		return ShFail
	}

	//-loglevel quiet -y %s -i %s %s -i %s -filter_complex amix=inputs=2:duration=longest -ar %s -ab %s -ac %s -f mp3
	leftCodes := getDecoderCodec(leftInCode)
	rightCodes := getDecoderCodec(rightInCode)

	args := []string{"-hide_banner", "-y"}
	tail := []string{"-filter_complex", "amix=inputs=2:duration=longest", "-ar", strconv.Itoa(ar), "-ab", strconv.Itoa(ab), "-ac", strconv.Itoa(ac), "-f", format, outFileName}

	if IS_QUIET_CMD {
		args = append(args, levelQuiet...)
	}

	waitArgs := getWaitTimes(waitTime)
	if len(waitArgs) > 1 {
		args = append(args, waitArgs...)
	}

	args = append(args, leftCodes...)
	args = append(args, []string{"-i", leftFileName}...)
	args = append(args, rightCodes...)
	args = append(args, []string{"-i", rightFileName}...)
	args = append(args, tail...)
	return cmdRun(tag, waitTime, "Mix2MonoLong", args, outFileName)
}

func Merge2Stereo(tag, leftFileName, rightFileName, outFileName string, ar, ab int, codec string, leftInCode, rightInCode int, format string) int {
	waitTime, code := checkPairLegs(tag, leftFileName, rightFileName)
	if code == ShFail {
		return ShFail
	}

	//-loglevel quiet -y %s -i %s %s -i %s -filter_complex  [0][1]amerge[aout] -map [aout] -acodec libmp3lame  -ar %s -ab %s -ac 2 -f mp3
	leftCodes := getDecoderCodec(leftInCode)
	rightCodes := getDecoderCodec(rightInCode)

	args := []string{"-hide_banner", "-y"}
	waitArgs := getWaitTimes(waitTime)
	tail1 := []string{"-filter_complex", "[0][1]amerge[aout]", "-map", "[aout]", "-acodec", codec, "-ar", strconv.Itoa(ar)}
	tail2 := []string{"-ab", strconv.Itoa(ab)}
	tail3 := []string{"-f", format, outFileName}
	if IS_QUIET_CMD {
		args = append(args, levelQuiet...)
	}
	if len(waitArgs) > 1 {
		args = append(args, waitArgs...)
	}

	args = append(args, leftCodes...)
	args = append(args, []string{"-i", leftFileName}...)
	args = append(args, rightCodes...)
	args = append(args, []string{"-i", rightFileName}...)
	args = append(args, tail1...)
	if ab > 0 {
		args = append(args, tail2...)
	}
	args = append(args, tail3...)
	return cmdRun(tag, waitTime, "Merge2Stereo", args, outFileName)
}

func Audio2Mono(tag, srcPath, outFileName string, ar, ab, ac, inCode int, format string) int {
	srcLen, err := util.FileLength(srcPath)
	if srcLen < 10 {
		log.WarnK(tag, "Audio2Mono,srcPath=%v not exist,err=%v", srcPath, err)
		return ShFail
	}
	waitTime := GetWaitTimeMs(srcLen)
	//-loglevel quiet %s -i %s -ar %s -ab %s -ac %s -y  -f mp3
	srcCodes := getDecoderCodec(inCode)
	args := []string{"-hide_banner", "-y"}
	argAb := []string{"-ab", strconv.Itoa(ab)}
	tail := []string{"-ar", strconv.Itoa(ar), "-ac", strconv.Itoa(ac), "-f", format, outFileName}
	if IS_QUIET_CMD {
		args = append(args, levelQuiet...)
	}

	waitArgs := getWaitTimes(waitTime)
	if len(waitArgs) > 1 {
		args = append(args, waitArgs...)
	}

	args = append(args, srcCodes...)
	args = append(args, []string{"-i", srcPath}...)
	if ab > 0 {
		args = append(args, argAb...)
	}
	args = append(args, tail...)
	return cmdRun(tag, waitTime, "Audio2Mono", args, outFileName)
}

func RightShortMerge(tag, left, right, outPath, format string, ar, ab int, codec string, leftInCode, rightInCode int) int {
	//-loglevel quiet -y %s -i %s %s -i %s

	filterComplex := "[1:a]apad[a1];[0:a][a1]amerge[aout]"
	return shortMergeStereo(tag, left, right, outPath, format, ar, ab, codec, leftInCode, rightInCode, filterComplex, "RightShortMerge")
}

func LeftShortMerge(tag, left, right, outPath, format string, ar, ab int, codec string, leftInCode, rightInCode int) int {
	//-loglevel quiet -y %s -i %s %s -i %s
	//-filter_complex "[0]apad[a];[a][1]amerge[aout]" -map "[aout]" -acodec libmp3lame %s -ar %s -ab %s -ac 2 -f %s
	//-filter_complex "[0]apad[a];[a][1]amerge[aout]" -map "[aout]" -acodec %s -ar %s -ac 2 -f wav

	filterComplex := "[0]apad[a];[a][1]amerge[aout]"
	return shortMergeStereo(tag, left, right, outPath, format, ar, ab, codec, leftInCode, rightInCode, filterComplex, "LeftShortMerge")
}

func RightEmptyMerge(tag, left, right, outPath, format string, ar, ab int, codec string, leftInCode int, rightInCode int) int {
	filterComplex := "[1:a]apad[a1];[0:a][a1]amerge[aout]"
	bizFlg := "RightEmptyMerge"

	//-loglevel quiet -y %s -i %s %s -i %s
	//-filter_complex "[1:a]apad[a1];[0:a][a1]amerge[aout]" -map "[aout]"  -acodec %s -ar %s -ac 2 -f wav
	//-filter_complex "[1:a]apad[a1];[0:a][a1]amerge[aout]" -map "[aout]" -acodec libmp3lame %s -ar %s -ab %s -ac 2 -f %s
	return shortMergeStereo(tag, left, right, outPath, format, ar, ab, codec, leftInCode, rightInCode, filterComplex, bizFlg)
}

func LeftEmptyMerge(tag, left, right, outPath, format string, ar, ab int, codec string, leftInCode int, rightInCode int) int {
	filterComplex := "[0]apad[a];[a][1]amerge[aout]"
	bizFlg := "LeftEmptyMerge"

	//-filter_complex "[0]apad[a];[a][1]amerge[aout]" -map "[aout]" -acodec libmp3lame %s -ar %s -ab %s -ac 2 -f %s
	//-filter_complex "[0]apad[a];[a][1]amerge[aout]" -map "[aout]" -acodec %s -ar %s -ac 2 -f wav
	return shortMergeStereo(tag, left, right, outPath, format, ar, ab, codec, leftInCode, rightInCode, filterComplex, bizFlg)
}

func shortMergeStereo(tag string, left string, right string, outPath string, format string, ar int, ab int, codec string, leftInCode int,
	rightInCode int, filterComplex string, bizFlg string) int {
	waitTime, code := checkPairLegs(tag, left, right)
	if code == ShFail {
		return ShFail
	}
	//-loglevel quiet -y %s -i %s %s -i %s
	//-filter_complex "[1:a]apad[a1];[0:a][a1]amerge[aout]" -map "[aout]"  -acodec %s -ar %s -ac 2 -f wav
	//-filter_complex "[1:a]apad[a1];[0:a][a1]amerge[aout]" -map "[aout]" -acodec libmp3lame %s -ar %s -ab %s -ac 2 -f %s
	leftCodes := getDecoderCodec(leftInCode)
	rightCodes := getDecoderCodec(rightInCode)

	args := []string{"-hide_banner", "-y"}
	tail1 := []string{"-filter_complex", filterComplex, "-map", "[aout]", "-acodec", codec, "-ar", strconv.Itoa(ar)}

	tailAb := []string{"-ab", strconv.Itoa(ab)}
	tailEnd := []string{"-ac", strconv.Itoa(2), "-f", format, outPath}
	if IS_QUIET_CMD {
		args = append(args, levelQuiet...)
	}

	waitArgs := getWaitTimes(waitTime)
	if len(waitArgs) > 1 {
		args = append(args, waitArgs...)
	}

	args = append(args, leftCodes...)
	args = append(args, []string{"-i", left}...)
	args = append(args, rightCodes...)
	args = append(args, []string{"-i", right}...)
	args = append(args, tail1...)
	if ab > 0 {
		args = append(args, tailAb...)
	}
	args = append(args, tailEnd...)
	return cmdRun(tag, waitTime, bizFlg, args, outPath)
}

func checkPairLegs(tag string, leftFileName string, rightFileName string) (int, int) {
	leftLen, err := util.FileLength(leftFileName)
	if leftLen < 10 {
		log.WarnK(tag, "checkPairLegs,left=%v not exist,err=%v", leftFileName, err)
		return 0, ShFail
	}
	var rightLen int64
	rightLen, err = util.FileLength(rightFileName)
	if rightLen < 10 {
		log.WarnK(tag, "checkPairLegs,right=%v not exist,err=%v", rightFileName, err)
		return 0, ShFail
	}
	maxSize := leftLen
	if maxSize > rightLen {
		maxSize = rightLen
	}
	waitTime := GetWaitTimeMs(maxSize)
	return waitTime, 0
}
