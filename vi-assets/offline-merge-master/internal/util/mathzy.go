package util

func Abs(x int) int {
	if x < 0 {
		return -x
	}
	return x
}

func MathCeil(x float32) int {
	if x == float32(int32(x)) {
		return int(x)
	}
	if x > 0 {
		return int(int32(x)) + 1
	}

	return int(int32(x))
}
