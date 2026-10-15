package metric

import (
	"github.com/gin-gonic/gin"
)

type ObserveType uint8

const (
	Summary ObserveType = iota + 1
	Histogram
)

type ObserveItem struct {
	// 观察类型
	OType ObserveType
	// 标签名列表
	Names []string
}

type Options struct {
	Id        string
	Version   string
	Namespace string

	// 元数据
	MetaData map[string]string

	// 统一指标监控
	CounterVec map[string][]string
	ObserveVec map[string]ObserveItem

	// 标签值抽取函数
	ExtractFunc func(*gin.Context) []string
}

type Option func(*Options)

func WithID(id string) Option {
	return func(opt *Options) {
		opt.Id = id
	}
}

func WithVersion(version string) Option {
	return func(opt *Options) {
		opt.Version = version
	}
}

func WithNamespace(namespace string) Option {
	return func(opt *Options) {
		opt.Namespace = namespace
	}
}

func WithMetaData(md map[string]string) Option {
	return func(opt *Options) {
		opt.MetaData = md
	}
}

func WithCounter(vec map[string][]string) Option {
	return func(opt *Options) {
		opt.CounterVec = vec
	}
}

func WithObserve(vec map[string]ObserveItem) Option {
	return func(opt *Options) {
		opt.ObserveVec = vec
	}
}

func WithExtractFunc(fn func(*gin.Context) []string) Option {
	return func(opt *Options) {
		opt.ExtractFunc = fn
	}
}
