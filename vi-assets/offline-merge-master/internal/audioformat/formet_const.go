package audioformat

type AudioFormat int

const (
	UNKNOWN AudioFormat = iota
	AMR_WB
	AMR_NB
	AMR_FT_NO_FIT
	WAV_PCMA
	WAV_PCMU
	WAV_NO_COMPRESS
	G729
	PCM_S16_16K
	PCM_S16_8K
	PCM_F32_16K
	PCM_F32_8K
)

func (a AudioFormat) GetCode() int {
	switch a {
	case AMR_WB:
		return 1
	case AMR_NB:
		return 2
	case AMR_FT_NO_FIT:
		return 3
	case WAV_PCMA:
		return 4
	case WAV_PCMU:
		return 5
	case WAV_NO_COMPRESS:
		return 6
	case G729:
		return 7
	case PCM_S16_16K:
		return 8
	case PCM_S16_8K:
		return 9
	case PCM_F32_16K:
		return 10
	case PCM_F32_8K:
		return 11
	default:
		return -1
	}
}
