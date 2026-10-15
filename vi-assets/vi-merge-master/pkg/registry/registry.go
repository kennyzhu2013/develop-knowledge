package registry

import (
	"errors"
	"io"
	"time"
)

var ErrNodeNotFound = errors.New("node not found")

const (
	// The prefix of the app instance key
	defaultKeyPrefix = "/"

	// Lease time for app instance key
	defaultLeaseTTL = 30 * time.Second

	// Renewal Cycle for the lease of the app instance key
	defaultLeaseInterval = 15 * time.Second

	// The timeout for the registry func calling
	defaultTimeout = 5 * time.Second
)

type Registry interface {
	io.Closer
	// Register Application Instance
	Register(Node, ...RegisterNodeOption) error
	// Deregister Application Instance
	Deregister() error
	// GetInstance Retrieve Application Instance List
	GetInstance(string, ...GetNodeOption) (*Instance, error)

	// ChangeNodeStatus Change the node status of the current application instance,
	// if you wish to do so under certain special circumstances
	ChangeNodeStatus(Status) Status
	// NodeStatus Retrieve the node status of the current application instance
	NodeStatus() Status

	// ChangeNodeWeight Change the node weight of the current application instance,
	// if you wish to do so under certain special circumstances
	ChangeNodeWeight(int) int
	// NodeWeight Retrieve the node weight of the current application instance
	NodeWeight() int
}
