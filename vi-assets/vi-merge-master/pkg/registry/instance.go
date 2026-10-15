package registry

import "math"

type Instance struct {
	Nodes   []*Node
	Name    string
	Version string
	Round   uint64
}

func (i *Instance) Copy() *Instance {
	ii := *i
	ii.Nodes = make([]*Node, len(ii.Nodes))
	for j := range i.Nodes {
		n := *i.Nodes[j]
		ii.Nodes[j] = &n
	}
	return &ii
}

func (i *Instance) Filter(opts *getNodeOptions) *Instance {
	ii := *i
	ii.Nodes = make([]*Node, 0, len(ii.Nodes))
	for j := range i.Nodes {
		if len(opts.Version) != 0 && opts.Version != i.Nodes[j].Version {
			continue
		}
		if len(opts.Status) != 0 && opts.Status != i.Nodes[j].Status {
			continue
		}
		n := *i.Nodes[j]
		ii.Nodes = append(ii.Nodes, &n)
	}
	// Backfill Version from the surviving nodes: take the first node's version.
	// The same instance name is not expected to mix versions, so this is safe.
	if len(ii.Nodes) > 0 {
		ii.Version = ii.Nodes[0].Version
	}
	return &ii
}

func (i *Instance) Append(node *Node) {
	i.Nodes = append(i.Nodes, node)
	i.nextRound()
}

func (i *Instance) Update(node *Node) {
	for j := range i.Nodes {
		if i.Nodes[j].LeaseId == node.LeaseId || (i.Nodes[j].Host == node.Host && i.Nodes[j].Port == node.Port) {
			i.Nodes[j] = node
			i.nextRound()
			break
		}
	}
}

// Upsert replaces the node if one matches by LeaseId or Host:Port, otherwise
// appends it. Used for both Create and Modify watch events so the cache cannot
// accumulate duplicate entries and never silently drops a node that is present
// in etcd but missing from the cache.
func (i *Instance) Upsert(node *Node) {
	for j := range i.Nodes {
		if i.Nodes[j].LeaseId == node.LeaseId || (i.Nodes[j].Host == node.Host && i.Nodes[j].Port == node.Port) {
			i.Nodes[j] = node
			i.nextRound()
			return
		}
	}
	i.Nodes = append(i.Nodes, node)
	i.nextRound()
}

func (i *Instance) Delete(host, port string) *Node {
	pos := -1
	for j := range i.Nodes {
		if i.Nodes[j].Host == host && i.Nodes[j].Port == port {
			pos = j
			break
		}
	}
	if pos == -1 {
		return nil
	}
	node := i.Nodes[pos]
	if pos < len(i.Nodes)-1 {
		copy(i.Nodes[pos:], i.Nodes[pos+1:])
	}
	i.Nodes[len(i.Nodes)-1] = nil
	i.Nodes = i.Nodes[:len(i.Nodes)-1]
	i.nextRound()
	return node
}

func (i *Instance) IsEmpty() bool {
	return len(i.Nodes) == 0
}

func (i *Instance) nextRound() {
	if i.Round == math.MaxUint64 {
		i.Round = 1
		return
	}
	i.Round++
}
