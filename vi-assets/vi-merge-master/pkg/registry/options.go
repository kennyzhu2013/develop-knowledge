package registry

import (
	"time"
	"vi-merge/pkg/logger"

	clientv3 "go.etcd.io/etcd/client/v3"
)

type registerNodeOptions struct {
	TTL time.Duration
}

type RegisterNodeOption func(*registerNodeOptions)

func WithTTL(ttl time.Duration) RegisterNodeOption {
	return func(options *registerNodeOptions) {
		options.TTL = ttl
	}
}

type getNodeOptions struct {
	Prefix  string
	Status  Status
	Version string
	// Notification callback when a node changes
	Notice func(Action, Node)
}

type GetNodeOption func(*getNodeOptions)

func WithPrefix(prefix string) GetNodeOption {
	return func(options *getNodeOptions) {
		options.Prefix = prefix
	}
}

func WithStatus(status Status) GetNodeOption {
	return func(options *getNodeOptions) {
		options.Status = status
	}
}

func WithVersion(version string) GetNodeOption {
	return func(options *getNodeOptions) {
		options.Version = version
	}
}

func WithNotice(notice func(Action, Node)) GetNodeOption {
	return func(options *getNodeOptions) {
		options.Notice = notice
	}
}

type newRegistryOptions struct {
	Endpoints   []string
	Prefix      string
	TTL         time.Duration
	Interval    time.Duration
	DialTimeout time.Duration

	Cli *clientv3.Client
	Log *logger.Logger

	Username string
	Password string
	CertFile string
	KeyFile  string
}

type NewRegistryOption func(*newRegistryOptions)

func Endpoints(endpoints []string) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.Endpoints = endpoints
	}
}

func Prefix(prefix string) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.Prefix = prefix
	}
}

func TTL(ttl time.Duration) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.TTL = ttl
	}
}

func Interval(interval time.Duration) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.Interval = interval
	}
}

func DialTimeout(timeout time.Duration) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.DialTimeout = timeout
	}
}

func Logger(log *logger.Logger) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.Log = log
	}
}

func Client(cli *clientv3.Client) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.Cli = cli
	}
}

func Authorize(username, password string) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.Username = username
		options.Password = password
	}
}

func TLS(cert, key string) NewRegistryOption {
	return func(options *newRegistryOptions) {
		options.CertFile = cert
		options.KeyFile = key
	}
}
