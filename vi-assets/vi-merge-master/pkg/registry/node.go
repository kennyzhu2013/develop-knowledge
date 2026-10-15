package registry

import (
	"fmt"

	"github.com/bytedance/sonic"
)

type Action uint8

const (
	ActionPut Action = 1
	ActionMod Action = 2
	ActionDel Action = 3
)

type Status string

const (
	// NodeStatusUp
	// Normally, able to receive requests
	NodeStatusUp = "UP"

	// NodeStatusMaintenance
	// Possibly updating or repairing, temporarily not participating in load balancing
	NodeStatusMaintenance = "MAINTENANCE"

	// NodeStatusOverloaded
	// Load is too high, may not be able to effectively process more requests
	NodeStatusOverloaded = "OVERLOADED"
)

type Node struct {
	Id        string         `json:"id"`
	Name      string         `json:"name"`
	Host      string         `json:"host"`
	Port      string         `json:"port"`
	InnerHost string         `json:"innerHost"`
	InnerPort string         `json:"innerPort"`
	Status    Status         `json:"status"`
	Version   string         `json:"version"`
	LeaseId   string         `json:"leaseId"`
	Weight    int            `json:"weight"`
	Metadata  map[string]any `json:"metadata"`
}

func (n Node) String() string {
	return fmt.Sprintf("Id: %s, LeaseId: %s, Name: %s, Version: %s, Address: %s:%s, InnerAddress: %s:%s, Status: %v, Weight: %v, Metadata: %v", n.Id, n.LeaseId, n.Name, n.Version, n.Host, n.Port,
		n.InnerHost, n.InnerPort, n.Status, n.Weight, n.Metadata)
}

func (n Node) Equals(n1 *Node) bool {
	if n1 == nil {
		return false
	}
	// Lease identity only applies to registered nodes: two lease-less nodes
	// must not compare equal merely because both lease IDs are the empty
	// string, otherwise Set() would never retire stale members.
	if n1.LeaseId != "" && n1.LeaseId == n.LeaseId {
		return true
	}
	if n1.Name == n.Name && n1.Host == n.Host && n1.Port == n.Port && n1.Version == n.Version {
		return true
	}
	return false
}

func (n Node) Key() string {
	return fmt.Sprintf("%s/%s:%s", n.Name, n.Host, n.Port)
}

func (n Node) Val() string {
	val, _ := sonic.MarshalString(&n)
	return val
}

func toNode(val []byte) (*Node, error) {
	node := new(Node)
	if err := sonic.Unmarshal(val, node); err != nil {
		return nil, err
	}
	return node, nil
}
