package fsm

type Event struct {
	// The event name
	Name string
	// The state before the transition
	Src string
	// The state after the transition
	Dst string
	// An optional error that can be returned from a callback
	Err error
	// An optional list of arguments passed to the callback
	Args []interface{}
	// An internal flag set if the transition is canceled
	canceled bool
	// An internal flag set if the transition should be asynchronous
	async bool
	// Called in case the event is canceled
	cancelFunc func()
}

// Cancel can be called in before_<EVENT> or leave_<STATE> to cancel the
// current transition before it happens. It takes an optional error, which will
// overwrite e.Err if set before.
func (e *Event) Cancel(err ...error) {
	e.canceled = true
	e.cancelFunc()

	if len(err) > 0 {
		e.Err = err[0]
	}
}

// Async can be called in leave_<STATE> to do an asynchronous state transition.
//
// The current state transition will be on hold in the old state until a final
// call to Transition is made. This will complete the transition and possibly
// call the other callbacks.
func (e *Event) Async() {
	e.async = true
}