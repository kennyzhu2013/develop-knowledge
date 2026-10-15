package mediasource

import (
	"net/http"
	"sync/atomic"
	"time"
	"vi-media/pkg/concurrent/promise"
)

type DialOptions struct {
	// Unique ID
	Id string
	// Request header
	Header http.Header
	// The timeout of the handshake
	HandshakeTimeout time.Duration
	// The timeout of write
	WriteTimeout time.Duration
	// The timeout of read
	ReadTimeout time.Duration
	// Data processing handle
	WriteStream WriteStream
	// Asynchronous synchronizer
	ASynchronizer promise.ASynchronizer
	// Enable read loop
	ReadEnabled bool
	// Callback on success
	OnSuccess func(*Conn) error
	// Callback on ending
	Callback func(interface{}, error)
	// Internal err when exceptions occur
	err atomic.Value
}

type DialOption func(*DialOptions)

func ID(id string) DialOption {
	return func(opts *DialOptions) {
		opts.Id = id
	}
}

func Header(header http.Header) DialOption {
	return func(opts *DialOptions) {
		opts.Header = header
	}
}

func StreamHandler(writeStream WriteStream) DialOption {
	return func(opts *DialOptions) {
		opts.WriteStream = writeStream
	}
}

func ReadEnabled(readEnabled bool) DialOption {
	return func(opts *DialOptions) {
		opts.ReadEnabled = readEnabled
	}
}

func ASynchronizer(aSynchronizer promise.ASynchronizer) DialOption {
	return func(opts *DialOptions) {
		opts.ASynchronizer = aSynchronizer
	}
}

func HandshakeTimeout(timeout time.Duration) DialOption {
	return func(opts *DialOptions) {
		opts.HandshakeTimeout = timeout
	}
}

func WriteTimeout(timeout time.Duration) DialOption {
	return func(opts *DialOptions) {
		opts.WriteTimeout = timeout
	}
}

func ReadTimeout(timeout time.Duration) DialOption {
	return func(opts *DialOptions) {
		opts.ReadTimeout = timeout
	}
}

func OnSuccess(callback func(*Conn) error) DialOption {
	return func(opts *DialOptions) {
		opts.OnSuccess = callback
	}
}

func Callback(callback func(interface{}, error)) DialOption {
	return func(opts *DialOptions) {
		opts.Callback = callback
	}
}

type InboundOptions struct {
	DialOpts DialOptions
	// Callback on success
	OnSuccess func(*Inbound) error
	// Callback on ending
	Callback func(interface{}, error)
}

type InboundOption func(*InboundOptions)

func WithInboundOnSuccess(onSuccess func(*Inbound) error) InboundOption {
	return func(opts *InboundOptions) {
		opts.OnSuccess = onSuccess
	}
}

func WithInboundCallback(callback func(interface{}, error)) InboundOption {
	return func(opts *InboundOptions) {
		opts.Callback = callback
	}
}

type OutboundOptions struct {
	DialOpts DialOptions
	// Callback on success
	OnSuccess func(*Outbound) error
	// Callback on ending
	Callback func(interface{}, error)
	// The queue buf size for pending sending
	QSize int
}

type OutboundOption func(*OutboundOptions)

func WithOutboundOnSuccess(onSuccess func(*Outbound) error) OutboundOption {
	return func(opts *OutboundOptions) {
		opts.OnSuccess = onSuccess
	}
}

func WithOutboundCallback(callback func(interface{}, error)) OutboundOption {
	return func(opts *OutboundOptions) {
		opts.Callback = callback
	}
}

func WithOutboundQSize(qSize int) OutboundOption {
	return func(opts *OutboundOptions) {
		opts.QSize = qSize
		if opts.QSize <= 0 {
			opts.QSize = 15
		}
	}
}