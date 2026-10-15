package promise

type ASynchronizer interface {
	Async(fn func())
}

type defaultASynchronizer struct{}

func (a *defaultASynchronizer) Async(fn func()) {
	go fn()
}