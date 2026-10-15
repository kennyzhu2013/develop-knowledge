package logger

import (
	"bytes"
	"io"
	"sync"
	"time"
)

var eventPool = &sync.Pool{
	New: func() interface{} {
		return &Event{}
	},
}

type Event struct {
	level     Level
	key       string
	message   string
	timestamp string
	fields    []string
	buffer    bytes.Buffer
}

func newEvent(level Level, message string) *Event {
	e := eventPool.Get().(*Event)
	e.level = level
	e.message = message
	e.timestamp = time.Now().Format("2006-01-02 15:04:05.000")
	return e
}

func (e *Event) Fields(fields ...string) *Event {
	e.fields = fields
	return e
}

func (e *Event) Key(key string) *Event {
	e.key = key
	return e
}

func (e *Event) Msg(w io.Writer) (int, error) {
	cnt, err := w.Write(e.gen())
	e.buffer.Reset()
	eventPool.Put(e)
	return cnt, err
}

func (e *Event) Len() int {
	return 54 + len(e.level.String()) + len(e.message) + len(e.fields)
}

func (e *Event) gen() []byte {
	e.buffer.Grow(200)
	e.buffer.WriteString("[")
	e.buffer.WriteString(e.timestamp)
	e.buffer.WriteString("] ")
	for _, field := range e.fields {
		e.buffer.WriteString("[")
		e.buffer.WriteString(field)
		e.buffer.WriteString("] ")
	}
	e.buffer.WriteString("[")
	e.buffer.WriteString(e.level.String())
	e.buffer.WriteString("] - [log/output.go] - [")
	e.buffer.WriteString(e.key)
	e.buffer.WriteString("] - ")
	e.buffer.WriteString(e.message)
	e.buffer.WriteString("\n")
	return e.buffer.Bytes()
}