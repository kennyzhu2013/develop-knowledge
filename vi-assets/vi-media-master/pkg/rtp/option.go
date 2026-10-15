package rtp

type writtenOptions struct {
	mtu      int
	marker   bool
	fragment bool
	sameTs   bool
}

type WrittenOption interface {
	Apply(*writtenOptions)
}

type funcWrittenOption struct {
	fn func(*writtenOptions)
}

func (fdo *funcWrittenOption) Apply(opts *writtenOptions) {
	fdo.fn(opts)
}

func newFuncOption(fn func(*writtenOptions)) *funcWrittenOption {
	return &funcWrittenOption{
		fn: fn,
	}
}

func Marker() WrittenOption {
	return newFuncOption(func(opts *writtenOptions) {
		opts.marker = true
	})
}

func MTU(mtu int) WrittenOption {
	return newFuncOption(func(opts *writtenOptions) {
		opts.mtu = mtu
	})
}

func Fragment() WrittenOption {
	return newFuncOption(func(opts *writtenOptions) {
		opts.fragment = true
	})
}

func SameTS() WrittenOption {
	return newFuncOption(func(opts *writtenOptions) {
		opts.sameTs = true
	})
}