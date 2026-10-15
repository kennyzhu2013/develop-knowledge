package notify

import "time"

type Config struct {
	Timeout        time.Duration     // HTTP请求超时
	RetryTimes     int               // 失败重试次数
	RetryDelay     time.Duration     // 重试间隔
	DefaultHeaders map[string]string // 默认请求头

	WorkerNum       int // 常驻协程数量
	ChannelCapacity int // 任务队列缓冲长度
}

// DefaultConfig 默认配置
func DefaultConfig() *Config {
	return &Config{
		Timeout:    3 * time.Second,
		RetryTimes: 1,
		RetryDelay: 500 * time.Millisecond,
		DefaultHeaders: map[string]string{
			"Content-Type": "application/json;charset=utf-8",
		},
		WorkerNum:       1,
		ChannelCapacity: 20,
	}
}
