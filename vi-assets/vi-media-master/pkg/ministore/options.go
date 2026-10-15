package ministore

import "time"

type Option interface {
	apply(*CacheTable)
}

type funcOption struct {
	fn func(*CacheTable)
}

func (fdo *funcOption) apply(table *CacheTable) {
	fdo.fn(table)
}

func newFuncOption(fn func(*CacheTable)) *funcOption {
	return &funcOption{
		fn: fn,
	}
}

func LifeSpan(lifeSpan time.Duration) Option {
	return newFuncOption(func(x *CacheTable) {
		x.lifeSpan = lifeSpan
	})
}

func TableSize(size int) Option {
	return newFuncOption(func(x *CacheTable) {
		x.items = make(map[string]*CacheItem, size)
	})
}

func IOStream(ioStream IoStream) Option {
	return newFuncOption(func(x *CacheTable) {
		x.IoStream = ioStream
	})
}

func LbPolicy(policy LBPolicy) Option {
	return newFuncOption(func(x *CacheTable) {
		x.LBPolicy = policy
	})
}

func DataOp(dataOp func(*CacheTable, string, []byte)) Option {
	return newFuncOption(func(x *CacheTable) {
		x.dataOp = dataOp
	})
}

func ExpireOp(expireOp func(*CacheTable, string)) Option {
	return newFuncOption(func(x *CacheTable) {
		x.expireOp = expireOp
	})
}

func LoadPreOp(loadPreOp func(*CacheItem)) Option {
	return newFuncOption(func(x *CacheTable) {
		x.loadPreOp = loadPreOp
	})
}