package dtmf

import "cmic.chinamobile.com/ziyan/go-sdk/logger"

type Option interface {
	apply(*DTMF)
}

type funcOption struct {
	fn func(*DTMF)
}

func (fdo *funcOption) apply(dtmf *DTMF) {
	fdo.fn(dtmf)
}

func newFuncOption(fn func(*DTMF)) *funcOption {
	return &funcOption{
		fn: fn,
	}
}

func Len(len int) Option {
	return newFuncOption(func(d *DTMF) {
		d.Len = len
	})
}

func EndSign(endSign string) Option {
	return newFuncOption(func(d *DTMF) {
		d.EndSign = endSign
	})
}

func BackSign(backSign string) Option {
	return newFuncOption(func(d *DTMF) {
		d.BackSign = backSign
	})
}

func Timeout(timeout int) Option {
	return newFuncOption(func(d *DTMF) {
		d.Timeout = timeout
	})
}

func IntervalTimeout(timeout int) Option {
	return newFuncOption(func(d *DTMF) {
		d.IntervalTimeout = timeout
	})
}

func Mode(mode int) Option {
	return newFuncOption(func(d *DTMF) {
		d.Mode = mode
	})
}

func TimingMode(mode int) Option {
	return newFuncOption(func(d *DTMF) {
		d.TimingMode = mode
	})
}

func MuteState(state bool) Option {
	return newFuncOption(func(d *DTMF) {
		d.muteState = state
	})
}

func EndCallback(callback func(string, error)) Option {
	return newFuncOption(func(d *DTMF) {
		d.EndCallback = callback
	})
}

func OnceNoticeCallback(callback func()) Option {
	return newFuncOption(func(d *DTMF) {
		d.OnceNoticeCallback = callback
	})
}

type InBandOptions struct {
	SessionId string
	Logger    *logger.Logger
}

type InBandOption interface {
	apply(*InBandOptions)
}

type funcInBandOption struct {
	fn func(*InBandOptions)
}

func (fdo *funcInBandOption) apply(o *InBandOptions) {
	fdo.fn(o)
}

func newFuncInBandOption(fn func(*InBandOptions)) *funcInBandOption {
	return &funcInBandOption{
		fn: fn,
	}
}

func WithSessionId(sessionId string) InBandOption {
	return newFuncInBandOption(func(o *InBandOptions) {
		o.SessionId = sessionId
	})
}

func WithLogger(log *logger.Logger) InBandOption {
	return newFuncInBandOption(func(o *InBandOptions) {
		o.Logger = log
	})
}