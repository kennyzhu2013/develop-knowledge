package models

// ------------------- 标准化异常结构体 -------------------
type WsError struct {
	ErrorCode    int                    `json:"error_code"`      // 错误码（如CONN_001、BUS_001、SYS_001）
	ErrorType    string                 `json:"error_type"`      // 错误类型：connection/business/system
	Message      string                 `json:"message"`         // 错误描述
	Timestamp    int64                  `json:"timestamp"`       // 时间戳
	BusinessType string                 `json:"business_type"`   // 业务类型
	Extra        map[string]interface{} `json:"extra,omitempty"` // 扩展字段（可选）
}

// 错误码定义（分类管理）
const (
	ErrCodeBizNoProcessor   = -1001
	ErrCodeBizProcessFailed = -1002

	ERROR_BIZ_MERGE = "merge"
)

// NewWsError 创建标准化异常对象
func NewWsError(errorCode int, message string, extra map[string]interface{}) *WsError {
	return &WsError{
		ErrorCode: errorCode,
		Message:   message,
		Extra:     extra,
	}
}

// ToBusinessMessage 转换为业务消息（用于发送给客户端）
func (e *WsError) ToErrorMessage() *ErrorMessage {
	return &ErrorMessage{
		Code:    e.ErrorCode,
		Message: e.Message,
	}
}
