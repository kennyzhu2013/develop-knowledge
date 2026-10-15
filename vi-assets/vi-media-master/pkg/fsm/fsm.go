package fsm

import (
	"context"
	"errors"
	"strings"
	"sync"
	"time"
)

const (
	callbackNone int = iota
	callbackBeforeEvent
	callbackLeaveState
	callbackEnterState
	callbackAfterEvent

	prefixBeforeEvent = "before_"
	prefixLeaveState  = "leave_"
	prefixEnterState  = "enter_"
	prefixAfterEvent  = "after_"
)

type unCancel struct {
	context.Context
}

func (*unCancel) Deadline() (deadline time.Time, ok bool) { return }
func (*unCancel) Done() <-chan struct{}                   { return nil }
func (*unCancel) Err() error                              { return nil }

// A context which ignores the cancellation of the parent and only keeps the values.
// This is useful to keep a background task running while the initial request is finished.
func unCancelContext(ctx context.Context) (context.Context, context.CancelFunc) {
	return context.WithCancel(&unCancel{ctx})
}

type eKey struct {
	// The name of the event
	eName string
	// The source from where the eName can transition
	src string
}

type cKey struct {
	// Either the name of a state or an event depending on which
	// callback type the key refers to
	// It can also be "" for a non-targeted callback like before_event
	target string
	// The situation when the callback will be run
	callbackType int
}

type EventDesc struct {
	// The event name used when calling for a transition
	Name string
	// A slice of source states that the FSM must be in to perform a state transition
	Src []string
	// The destination state that the FSM will be in if the transition succeeds
	Dst string
}

type Events []EventDesc
type Callback func(context.Context, *Event)
type Callbacks map[string]Callback

type FSM struct {
	// The state that currently in
	current string
	// Maps events and source states to destination states
	transitions map[eKey]string
	// Maps events and targets to callback functions
	callbacks map[cKey]Callback
	// The internal transition functions used either directly
	// or when Transition is called in an asynchronous state transition
	transition func()
	// stateMu guards access to the current state.
	stateMu sync.RWMutex
	// eventMu guards access to Event() and Transition().
	eventMu sync.Mutex
}

// NewFSM constructs a FSM from events and callbacks.
//
// The events and transitions are specified as a slice of Event structs
// specified as Events. Each Event is mapped to one or more internal
// transitions from Event.Src to Event.Dst.
//
// Callbacks are added as a map specified as Callbacks where the key is parsed
// as the callback event as follows, and called in the same order:
//  1. before_<EVENT> - called before event named <EVENT>
//  2. before_event - called before all events
//  3. leave_<OLD_STATE> - called before leaving <OLD_STATE>
//  4. leave_state - called before leaving all states
//  5. enter_<NEW_STATE> - called after entering <NEW_STATE>
//  6. enter_state - called after entering all states
//  7. after_<EVENT> - called after event named <EVENT>
//  8. after_event - called after all events
//
// There are also two short form versions for the most commonly used callbacks.
// They are simply the name of the event or state:
//  1. <NEW_STATE> - called after entering <NEW_STATE>
//  2. <EVENT> - called after event named <EVENT>
//
// If both a shorthand version and a full version is specified it is undefined
// which version of the callback will end up in the internal map. This is due
// to the pseudo random nature of Go maps. No checking for multiple keys is
// currently performed.
func NewFSM(initial string, events []EventDesc, callbacks map[string]Callback) *FSM {
	f := &FSM{
		current:     initial,
		transitions: make(map[eKey]string),
		callbacks:   make(map[cKey]Callback),
	}
	// Build transition map and store sets of all events and states
	allEvents := make(map[string]struct{})
	allStates := make(map[string]struct{})
	for _, e := range events {
		allEvents[e.Name] = struct{}{}
		for _, src := range e.Src {
			f.transitions[eKey{e.Name, src}] = e.Dst
			allStates[src] = struct{}{}
			allStates[e.Dst] = struct{}{}
		}
	}
	// Map all callbacks to events/states
	for name, fn := range callbacks {
		var target string
		var callbackType int
		switch {
		case strings.HasPrefix(name, prefixBeforeEvent):
			target = strings.TrimPrefix(name, prefixBeforeEvent)
			if target == "event" {
				target = ""
				callbackType = callbackBeforeEvent
			} else if _, ok := allEvents[target]; ok {
				callbackType = callbackBeforeEvent
			}
		case strings.HasPrefix(name, prefixLeaveState):
			target = strings.TrimPrefix(name, prefixLeaveState)
			if target == "state" {
				target = ""
				callbackType = callbackLeaveState
			} else if _, ok := allStates[target]; ok {
				callbackType = callbackLeaveState
			}
		case strings.HasPrefix(name, prefixEnterState):
			target = strings.TrimPrefix(name, prefixEnterState)
			if target == "state" {
				target = ""
				callbackType = callbackEnterState
			} else if _, ok := allStates[target]; ok {
				callbackType = callbackEnterState
			}
		case strings.HasPrefix(name, prefixAfterEvent):
			target = strings.TrimPrefix(name, prefixAfterEvent)
			if target == "event" {
				target = ""
				callbackType = callbackAfterEvent
			} else if _, ok := allEvents[target]; ok {
				callbackType = callbackAfterEvent
			}
		default:
			target = name
			if _, ok := allStates[target]; ok {
				callbackType = callbackEnterState
			} else if _, ok = allEvents[target]; ok {
				callbackType = callbackAfterEvent
			}
		}
		if callbackType != callbackNone {
			f.callbacks[cKey{target, callbackType}] = fn
		}
	}
	return f
}

// Current returns the current state of the FSM
func (f *FSM) Current() string {
	f.stateMu.RLock()
	defer f.stateMu.RUnlock()
	return f.current
}

// Is returns true if state is the current state
func (f *FSM) Is(state string) bool {
	f.stateMu.RLock()
	defer f.stateMu.RUnlock()
	return state == f.current
}

// SetState allows the user to move to the given state from current state
// The call does not trigger any callbacks, if defined
func (f *FSM) SetState(state string) {
	f.stateMu.Lock()
	defer f.stateMu.Unlock()
	f.current = state
}

// Can returns true if event can occur in the current state
func (f *FSM) Can(event string) (string, bool) {
	f.stateMu.RLock()
	defer f.stateMu.RUnlock()
	dst, ok := f.transitions[eKey{event, f.current}]
	return dst, ok && (f.transition == nil)
}

// AvailableTransitions returns a list of transitions available in the current state
func (f *FSM) AvailableTransitions() []string {
	f.stateMu.RLock()
	defer f.stateMu.RUnlock()
	var transitions []string
	for key := range f.transitions {
		if key.src == f.current {
			transitions = append(transitions, key.eName)
		}
	}
	return transitions
}

func (f *FSM) Event(ctx context.Context, event *Event) error {
	// Get the dst state for this event
	dst, ok := f.transitions[eKey{event.Name, f.current}]
	if !ok {
		for ek := range f.transitions {
			if ek.eName == event.Name {
				return InvalidEventError{event.Name, f.current}
			}
		}
		return UnknownEventError{event.Name}
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	// Pre-processing
	err := f.beforeEventCallbacks(ctx, event)
	if err != nil {
		return err
	}
	// No state transition
	if f.current == dst {
		f.afterEventCallbacks(ctx, event)
		return NoTransitionError{event.Err}
	}
	// Construct the transition and call it later
	transitionFunc := func(ctx context.Context, async bool) func() {
		return func() {
			if ctx.Err() != nil {
				if event.Err == nil {
					event.Err = ctx.Err()
				}
				return
			}
			f.stateMu.Lock()
			f.current = dst
			f.transition = nil // treat the state transition as done
			f.stateMu.Unlock()
			f.enterStateCallbacks(ctx, event)
			f.afterEventCallbacks(ctx, event)
		}
	}
	f.transition = transitionFunc(ctx, false)
	// Processing after leaving current state
	if err = f.leaveStateCallbacks(ctx, event); err != nil {
		var canceledError CanceledError
		if errors.As(err, &canceledError) {
			f.transition = nil
		} else {
			var asyncError AsyncError
			if errors.As(err, &asyncError) {
				uCtx, uCancel := unCancelContext(ctx)
				event.cancelFunc = uCancel
				asyncError.Ctx = uCtx
				asyncError.CancelTransition = uCancel
				f.transition = transitionFunc(uCtx, true)
				return asyncError
			}
		}
		return err
	}
	// Start transitioning
	f.stateMu.RUnlock()
	defer f.stateMu.RLock()
	err = f.doTransition()
	if err != nil {
		return InternalError{}
	}
	return event.Err
}

// EventWithArgs initiates a state transition with the named event
//
// The call takes a variable number of arguments that will be passed to the callback,
// if defined
//
// It will return nil if the state change is ok or one of these errors:
//   - event X inappropriate because previous transition did not complete
//   - event X inappropriate in current state Y
//   - event X does not exist
//   - internal error on state transition
//
// The last error should never occur in this situation and is a sign of an internal bug
func (f *FSM) EventWithArgs(ctx context.Context, event string, args ...interface{}) error {
	f.eventMu.Lock()
	// in order to always unlock the event mutex, the defer is added
	// in case the state transition goes through and enter/after callbacks
	// are called; because these must be able to trigger new state
	// transitions, it is explicitly unlocked in the code below
	var unlocked bool
	defer func() {
		if !unlocked {
			f.eventMu.Unlock()
		}
	}()

	f.stateMu.RLock()
	defer f.stateMu.RUnlock()

	if f.transition != nil {
		return InTransitionError{event}
	}

	// Get the dst state for this event
	dst, ok := f.transitions[eKey{event, f.current}]
	if !ok {
		for ek := range f.transitions {
			if ek.eName == event {
				return InvalidEventError{event, f.current}
			}
		}
		return UnknownEventError{event}
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	e := &Event{event, f.current, dst, nil, args, false, false, cancel}
	// Pre-processing
	err := f.beforeEventCallbacks(ctx, e)
	if err != nil {
		return err
	}
	// No state transition
	if f.current == dst {
		f.stateMu.RUnlock()
		defer f.stateMu.RLock()
		f.eventMu.Unlock()
		unlocked = true
		f.afterEventCallbacks(ctx, e)
		return NoTransitionError{e.Err}
	}
	// Construct the transition and call it later
	transitionFunc := func(ctx context.Context, async bool) func() {
		return func() {
			if ctx.Err() != nil {
				if e.Err == nil {
					e.Err = ctx.Err()
				}
				return
			}
			f.stateMu.Lock()
			f.current = dst
			f.transition = nil // treat the state transition as done
			f.stateMu.Unlock()

			// at this point, we unlock the event mutex in order to allow
			// to enter state callbacks to trigger another transition
			// for asynchronous state transitions this doesn't happen because
			// the event mutex has already been unlocked
			if !async {
				f.eventMu.Unlock()
				unlocked = true
			}
			f.enterStateCallbacks(ctx, e)
			f.afterEventCallbacks(ctx, e)
		}
	}
	f.transition = transitionFunc(ctx, false)
	// Processing after leaving current state
	if err = f.leaveStateCallbacks(ctx, e); err != nil {
		var canceledError CanceledError
		if errors.As(err, &canceledError) {
			f.transition = nil
		} else {
			var asyncError AsyncError
			if errors.As(err, &asyncError) {
				uCtx, uCancel := unCancelContext(ctx)
				e.cancelFunc = uCancel
				asyncError.Ctx = uCtx
				asyncError.CancelTransition = uCancel
				f.transition = transitionFunc(uCtx, true)
				return asyncError
			}
		}
		return err
	}
	// Start transitioning
	f.stateMu.RUnlock()
	defer f.stateMu.RLock()
	err = f.doTransition()
	if err != nil {
		return InternalError{}
	}
	return e.Err
}

func (f *FSM) doTransition() error {
	if f.transition == nil {
		return NotInTransitionError{}
	}
	f.transition()
	return nil
}

// beforeEventCallbacks calls the before_ callbacks
func (f *FSM) beforeEventCallbacks(ctx context.Context, e *Event) error {
	if fn, ok := f.callbacks[cKey{e.Name, callbackBeforeEvent}]; ok {
		fn(ctx, e)
		if e.canceled {
			return CanceledError{e.Err}
		}
	}
	if fn, ok := f.callbacks[cKey{"", callbackBeforeEvent}]; ok {
		fn(ctx, e)
		if e.canceled {
			return CanceledError{e.Err}
		}
	}
	return nil
}

// leaveStateCallbacks calls the leave_ callbacks
func (f *FSM) leaveStateCallbacks(ctx context.Context, e *Event) error {
	if fn, ok := f.callbacks[cKey{f.current, callbackLeaveState}]; ok {
		fn(ctx, e)
		if e.canceled {
			return CanceledError{e.Err}
		} else if e.async {
			return AsyncError{Err: e.Err}
		}
	}
	if fn, ok := f.callbacks[cKey{"", callbackLeaveState}]; ok {
		fn(ctx, e)
		if e.canceled {
			return CanceledError{e.Err}
		} else if e.async {
			return AsyncError{Err: e.Err}
		}
	}
	return nil
}

// enterStateCallbacks calls the enter_ callbacks
func (f *FSM) enterStateCallbacks(ctx context.Context, e *Event) {
	if fn, ok := f.callbacks[cKey{f.current, callbackEnterState}]; ok {
		fn(ctx, e)
	}
	if fn, ok := f.callbacks[cKey{"", callbackEnterState}]; ok {
		fn(ctx, e)
	}
}

// afterEventCallbacks calls the after_ callbacks
func (f *FSM) afterEventCallbacks(ctx context.Context, e *Event) {
	if fn, ok := f.callbacks[cKey{e.Name, callbackAfterEvent}]; ok {
		fn(ctx, e)
	}
	if fn, ok := f.callbacks[cKey{"", callbackAfterEvent}]; ok {
		fn(ctx, e)
	}
}