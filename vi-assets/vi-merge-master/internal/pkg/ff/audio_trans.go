package ff

import "strconv"

const (
	Libmp3lame = "libmp3lame"

	AMR_NB        = "amrnb"
	AMR_WB        = "amrwb"
	PCM_S16LE_8k  = "pcm_s16le_8k"
	PCM_S16LE_16k = "pcm_s16le_16k"
	PCM_F32_8k    = "pcm_f32_8k"
	PCM_F32_16k   = "pcm_f32_16k"
)

func Pcm2Mp3Ac2(tag string, ac int, srcSample, destSample int, suffix string, srcFile string, outFile string) int {
	//ffmpeg  -y -f s16le -ar 16000  -ac 2 -i stereo_s16le_16k.pcm  -ar 8000 -ab 32k -ac 2 -f mp3  stereo_s16le_16k.mp3

	waitTime := GetWaitTimeMs(1024 * 1024)
	const Mp3BitRate = "32k"
	args := []string{"-hide_banner", "-y"}
	//waitArgs := getWaitTimes(strconv.Itoa(waitTime) + "ms")

	head := []string{"-f", "s16le", "-ar", strconv.Itoa(srcSample), "-ac", strconv.Itoa(ac)}
	tail := []string{"-ar", strconv.Itoa(destSample), "-ab", Mp3BitRate, "-ac", strconv.Itoa(ac), "-f", suffix, outFile}

	if IS_QUIET_CMD {
		args = append(args, levelQuiet...)
	}
	//if len(waitArgs) > 1 {
	//	args = append(args, waitArgs...)
	//}
	args = append(args, head...)
	args = append(args, []string{"-i", srcFile}...)
	args = append(args, tail...)

	return cmdRun(tag, waitTime, "pcm2Mp3Ac2", args, outFile)
}

func getDecoderCodec(inCode string) []string {
	switch inCode {
	case AMR_NB:
		return []string{"-acodec", "libopencore_amrnb"}
	case AMR_WB:
		return []string{"-acodec", "libopencore_amrwb"}
	case PCM_S16LE_8k:
		return getPcmConfig("S16LE", 8000)
	case PCM_S16LE_16k:
		return getPcmConfig("S16LE", 16000)
	case PCM_F32_8k:
		return getPcmConfig("F32LE", 8000)
	case PCM_F32_16k:
		return getPcmConfig("F32LE", 16000)
	default:
		return []string{}
	}
}
