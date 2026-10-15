package dto

type Result[T any] struct {
	Code    int    `json:"code"`
	Data    T      `json:"data"`
	Message string `json:"message"`
}

func Success(code int) *Result[string] {
	return &Result[string]{Code: code, Message: "success"}
}

func SuccessWithData[T any](code int, data T) *Result[T] {
	return &Result[T]{code, data, "success"}
}

func Fail(code int, message string) *Result[string] {
	return &Result[string]{Code: code, Message: message}
}