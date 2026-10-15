package httpinterface

// code 到常量名的反向映射：通过值找 code 名
var codeToName = map[int]string{
	FILE_SUC_CODE:              "FILE_SUC_CODE",
	CONNECT_ERROR_CODE:         "CONNECT_ERROR_CODE",
	CONNECT_ERROR_5XX:          "CONNECT_ERROR_5XX",
	FILE_FAIL_CODE:             "FILE_FAIL_CODE",
	CONNECT_ERROR_READ_TIMEOUT: "CONNECT_ERROR_READ_TIMEOUT",
	CONNECT_ERROR_NO_ACCESS:    "CONNECT_ERROR_NO_ACCESS",
	CONNECT_ERROR_404:          "CONNECT_ERROR_404",
	CONNECT_ERROR_SOCKET:       "CONNECT_ERROR_SOCKET",
}

// Name 通过数值反查常量名；未知返回 "UNKNOWN"
func CodeName(code int) string {
	if name, ok := codeToName[code]; ok {
		return name
	}
	return "UNKNOWN"
}
