package registry

import (
	"context"
	"crypto/tls"
	"errors"
	"math"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	"vi-merge/pkg/logger"
	"vi-merge/pkg/process"

	"go.etcd.io/etcd/api/v3/v3rpc/rpctypes"
	clientv3 "go.etcd.io/etcd/client/v3"
)

type command struct {
	status Status
	weight int
}

type Etcd struct {
	opts *newRegistryOptions

	instance atomic.Pointer[Node]
	// Cache
	nodes map[string]*Instance

	cmd  chan command
	exit chan struct{}

	once sync.Once
	mu   sync.RWMutex
}

func NewEtcd(opts ...NewRegistryOption) (*Etcd, error) {
	options := &newRegistryOptions{}
	for _, opt := range opts {
		opt(options)
	}
	if len(options.Endpoints) == 0 {
		options.Endpoints = []string{"127.0.0.1:2379"}
	}
	if len(options.Prefix) == 0 {
		options.Prefix = defaultKeyPrefix
	} else {
		options.Prefix += defaultKeyPrefix
	}
	if options.TTL <= 0 {
		options.TTL = defaultLeaseTTL
	}
	if options.Interval <= 0 {
		options.Interval = defaultLeaseInterval
	}
	if options.DialTimeout <= 0 {
		options.DialTimeout = defaultTimeout
	}
	if options.Cli == nil {
		var tLs *tls.Config
		if len(options.CertFile) != 0 || len(options.KeyFile) != 0 {
			cert, err := tls.LoadX509KeyPair(options.CertFile, options.KeyFile)
			if err != nil {
				return nil, err
			}
			tLs = &tls.Config{Certificates: []tls.Certificate{cert}}
		}
		client, err := clientv3.New(clientv3.Config{
			Endpoints:   options.Endpoints,
			DialTimeout: options.DialTimeout,
			TLS:         tLs,
			Username:    options.Username,
			Password:    options.Password,
		})
		if err != nil {
			return nil, err
		}
		options.Cli = client
	}
	return &Etcd{opts: options, nodes: make(map[string]*Instance), cmd: make(chan command, 1), exit: make(chan struct{})}, nil
}

func (e *Etcd) Register(node Node, opts ...RegisterNodeOption) error {
	// Claim the registration slot atomically: only one Register may proceed, so
	// concurrent calls cannot both Grant/Put and spawn duplicate keepalive
	// loops that thrash the same key's lease. Losers bail out immediately.
	if !e.instance.CompareAndSwap(nil, &node) {
		return errors.New("the node has already registered")
	}
	options := new(registerNodeOptions)
	for _, opt := range opts {
		opt(options)
	}
	if options.TTL <= 0 {
		options.TTL = e.opts.TTL
	}
	// Allocate lease ID
	ctx, cancel := context.WithTimeout(context.TODO(), e.opts.DialTimeout)
	defer cancel()
	gResp, err := e.opts.Cli.Grant(ctx, int64(math.Ceil(options.TTL.Seconds())))
	if err != nil {
		e.instance.Store(nil)
		return err
	}
	// Write LeaseId on a copy and publish it atomically: the registered *Node
	// stays immutable (no data race with concurrent Deregister readers), and a
	// Deregister that ran during the Grant window invalidates the claim so the
	// key cannot be re-created after Deregister deleted it (permanent ghost).
	n := node
	n.LeaseId = strconv.FormatInt(int64(gResp.ID), 16)
	if !e.instance.CompareAndSwap(&node, &n) {
		// Deregistered while granting: revoke the fresh lease and abandon the
		// registration instead of re-creating the key after Deregister deleted it.
		_, _ = e.opts.Cli.Revoke(ctx, gResp.ID)
		return errors.New("the node has already deregistered")
	}
	// Put KV
	_, err = e.opts.Cli.Put(ctx, e.opts.Prefix+n.Key(), n.Val(), clientv3.WithLease(gResp.ID))
	if err != nil {
		e.instance.Store(nil)
		return err
	}
	// Start lease renew
	go e.keepaliveLoop(gResp.ID)
	// Start watching the key itself so an external deletion is healed.
	go e.selfHealLoop()
	return nil
}

func (e *Etcd) Deregister() error {
	// Atomically take and clear the registered node. Swap makes Deregister
	// idempotent and concurrency-safe: only the first caller gets the node and
	// proceeds; later/parallel callers get nil and return. This also removes the
	// prior TOCTOU where a second concurrent call could Load() nil and panic on
	// node.Key().
	node := e.instance.Swap(nil)
	if node == nil {
		return nil
	}
	ctx, cancel := context.WithTimeout(context.TODO(), e.opts.DialTimeout)
	defer cancel()
	// Delete KV
	_, err := e.opts.Cli.Delete(ctx, e.opts.Prefix+node.Key())
	if err != nil {
		return err
	}
	// Revoke a lease
	leaseId, _ := strconv.ParseInt(node.LeaseId, 16, 64)
	_, err = e.opts.Cli.Revoke(ctx, clientv3.LeaseID(leaseId))
	return err
}

func (e *Etcd) GetInstance(name string, opts ...GetNodeOption) (*Instance, error) {
	options := new(getNodeOptions)
	for _, opt := range opts {
		opt(options)
	}
	if len(options.Prefix) == 0 {
		// Fall back to the registration prefix (NewEtcd's Prefix option, "/" by
		// default) so GetInstance finds nodes registered under this client's own
		// namespace without callers repeating it. Explicit WithPrefix still
		// overrides for cross-namespace queries.
		options.Prefix = e.opts.Prefix
	} else if !strings.HasSuffix(options.Prefix, defaultKeyPrefix) {
		options.Prefix += defaultKeyPrefix
	}
	e.mu.RLock()
	instance := e.nodes[options.Prefix+name]
	if instance != nil {
		instance = instance.Filter(options)
	}
	e.mu.RUnlock()
	// Cache hit: return only when it actually has matching nodes. An empty
	// cached instance (e.g. service not registered yet, or all filtered out)
	// must report "not found" uniformly with a first-time miss.
	if instance != nil {
		if instance.IsEmpty() {
			return nil, ErrNodeNotFound
		}
		return instance, nil
	}
	// Cache miss: query etcd.
	ctx, cancel := context.WithTimeout(context.TODO(), e.opts.DialTimeout)
	defer cancel()
	gResp, err := e.opts.Cli.Get(ctx, options.Prefix+name+defaultKeyPrefix, clientv3.WithPrefix(), clientv3.WithSort(clientv3.SortByKey, clientv3.SortDescend))
	if err != nil {
		return nil, err
	}
	// Unparseable entries are skipped (and logged) but never let the cache
	// fall into a watchless "not found" state: creation below is unconditional.
	nodes := make([]*Node, 0, len(gResp.Kvs))
	for _, kv := range gResp.Kvs {
		node, nErr := toNode(kv.Value)
		if nErr != nil {
			continue
		}
		nodes = append(nodes, node)
	}
	// Creating the cache entry and claiming its watch happen in one critical
	// section: whoever creates the entry starts the watch, so an entry never
	// exists without a watch. No matter how many nodes survive the parse or
	// the status/version filter, the entry is never watcherLess. Concurrent
	// callers only reuse the entry and its already-running watch. Zero KVs
	// and fully-unparseable data both converge here: an empty entry plus a
	// watch that fills the cache later.
	var startWatch bool
	e.mu.Lock()
	if e.nodes[options.Prefix+name] == nil {
		e.nodes[options.Prefix+name] = &Instance{Name: name, Nodes: nodes, Round: 1}
		startWatch = true
	}
	instance = e.nodes[options.Prefix+name].Filter(options)
	e.mu.Unlock()
	if startWatch {
		go e.updateLoop(options.Prefix, name, options.Notice, gResp.Header.Revision)
	}
	if instance.IsEmpty() {
		return nil, ErrNodeNotFound
	}
	return instance, nil
}

func (e *Etcd) ChangeNodeStatus(status Status) Status {
	node := e.instance.Load()
	if node != nil {
		prev := node.Status
		select {
		case <-e.exit:
			return prev
		case e.cmd <- command{status: status}:
			return prev
		}
	}
	return ""
}

func (e *Etcd) NodeStatus() Status {
	node := e.instance.Load()
	if node != nil {
		return node.Status
	}
	return ""
}

func (e *Etcd) ChangeNodeWeight(weight int) int {
	node := e.instance.Load()
	if node != nil {
		prev := node.Weight
		select {
		case <-e.exit:
			return prev
		case e.cmd <- command{weight: weight}:
			return prev
		}
	}
	return 0
}

func (e *Etcd) NodeWeight() int {
	node := e.instance.Load()
	if node != nil {
		return node.Weight
	}
	return 0
}

func (e *Etcd) Close() error {
	var err error
	e.once.Do(func() {
		close(e.exit)
		_ = e.Deregister()
		err = e.opts.Cli.Close()
	})
	return err
}

func (e *Etcd) keepaliveLoop(id clientv3.LeaseID) {
	ticker := time.NewTicker(e.opts.Interval)
	defer ticker.Stop()
	for {
		// Deregister sets e.instance to nil; exit so this goroutine does not
		// leak and does not keep granting orphaned leases for a deleted key.
		if e.instance.Load() == nil {
			return
		}
		select {
		case <-e.exit:
			return
		default:
		}
		// Per-round recover: a panic must not permanently kill lease renewal.
		func() {
			defer func() {
				if v := recover(); v != nil && e.opts.Log != nil {
					e.opts.Log.ErrorF("Recovered from keepaliveLoop with %v, stack: %v", v, process.Stack())
				}
			}()
			select {
			case cmd := <-e.cmd:
				node := e.instance.Load()
				if node == nil {
					return
				}
				// copy-on-write: mutate a copy, then publish it with CAS so the
				// stored *Node stays immutable (concurrent NodeStatus/Deregister
				// readers never race on its fields) and a Deregister that landed
				// during the Put window cannot be undone by an unconditional
				// Store (the phantom re-registration ed2fef0/9348318 closed for
				// Register and the regrant path).
				n := *node
				if len(cmd.status) != 0 {
					n.Status = cmd.status
				} else {
					n.Weight = cmd.weight
				}
				ctx, cancel := context.WithTimeout(context.TODO(), e.opts.DialTimeout)
				defer cancel()
				_, err := e.opts.Cli.Put(ctx, e.opts.Prefix+n.Key(), n.Val(), clientv3.WithLease(id))
				if err != nil {
					if e.opts.Log != nil {
						e.opts.Log.WarnF("Update app instance status failed: %v", err)
					}
				} else if !e.instance.CompareAndSwap(node, &n) {
					// Deregistered (or re-registered) while the Put was in flight:
					// do not republish. The key this Put wrote is attached to the
					// lease Deregister revokes, so it cannot outlive Deregister.
					if e.opts.Log != nil {
						e.opts.Log.WarnF("Update app instance status failed: the node has already deregistered")
					}
				}
			case <-ticker.C:
				// One timeout bounds the whole recovery tick, so an unreachable
				// cluster cannot stall cmd/exit handling for longer than
				// DialTimeout (instead of 3x when set per call).
				ctx, cancel := context.WithTimeout(context.TODO(), e.opts.DialTimeout)
				defer cancel()
				_, err := e.opts.Cli.KeepAliveOnce(ctx, id)
				if err == nil || !errors.Is(err, rpctypes.ErrLeaseNotFound) {
					return
				}
				// Allocate new lease ID
				gResp, err := e.opts.Cli.Grant(ctx, int64(math.Ceil(e.opts.TTL.Seconds())))
				if err != nil {
					if e.opts.Log != nil {
						e.opts.Log.WarnF("Grant a new leaseID failed: %v", err)
					}
					return
				}
				// Keep the new ID local until the Put succeeds: if the Put fails,
				// id stays the (expired) old lease, so the next KeepAliveOnce will
				// return ErrLeaseNotFound again and this path is retried until the
				// key is rewritten or the node is deregistered. This prevents the
				// "lease alive but key permanently missing" state.
				nId := gResp.ID
				node := e.instance.Load()
				if node == nil {
					// Deregistered while regranting: do not write the key. The
					// fresh lease is never adopted or kept alive, so it expires
					// by itself.
					return
				}
				// copy-on-write: update LeaseId on a copy and publish it only if
				// the registered node is still the one we loaded, Otherwise a
				// concurrent Deregister (or cmd) swapped e.instance in the window
				// between the Load above and here, and publishing &n would re-create
				// the key after Deregister deleted it — a permanent ghost node
				// (the same class ed2fef0 closed for Register). Like Register's
				// CAS, a failed swap abandons the regrant: the unadopted fresh
				// lease carries no kept-alive key and expires by itself, so the
				// next tick re-enters the regrant path and converges.
				n := *node
				n.LeaseId = strconv.FormatInt(int64(nId), 16)
				_, err = e.opts.Cli.Put(ctx, e.opts.Prefix+n.Key(), n.Val(), clientv3.WithLease(nId))
				if err != nil {
					// Put failed: never adopt the new lease, so id stays the
					// expired old one and the next tick re-enters the regrant
					// path. The fresh lease carries no key and is never kept
					// alive, so it expires by itself; like Register's Put-failure
					// path, no explicit revoke is needed.
					if e.opts.Log != nil {
						e.opts.Log.WarnF("Refresh app instance failed: %v", err)
					}
					return
				}
				if !e.instance.CompareAndSwap(node, &n) {
					// Deregistered (or superseded) after we loaded, before the
					// Put landed: do not adopt the fresh lease. The key it wrote
					// is attached to this unadopted lease and is never kept alive,
					// so it expires by itself instead of outliving Deregister.
					return
				}
				id = nId // only adopt the new lease once the key is rewritten
			}
		}()
	}
}

// selfHealLoop watches the registered key itself so an external deletion
// (operator, competing writer, etcd maintenance) is detected and the key is
// rewritten on the same live lease. keepaliveLoop only proves the *lease* is
// alive — the *key* can vanish while the lease lives, leaving the node
// registered in the local view but invisible to every consumer. The Delete
// event triggers a re-Put bound to the lease recorded in e.instance, the same
// lease keepaliveLoop renews, so a later Deregister can still revoke it.
// Deregister first swaps e.instance to nil, so a Delete observed after that
// finds no node to resurrect; if this loop re-Puts while Deregister is
// revoking, the revoke deletes the key again. Both orderings let Deregister
// win, matching the CAS design of Register.
func (e *Etcd) selfHealLoop() {
	for {
		node := e.instance.Load()
		if node == nil {
			return // Deregistered: never resurrect
		}
		select {
		case <-e.exit:
			return
		default:
		}
		// Watch only the node's own exact key. WithFilterPut drops PUT events so
		// only deletes arrive; the handler still filters by type and key, because
		// the test fake shares one watch channel across all watches.
		wch := e.opts.Cli.Watch(clientv3.WithRequireLeader(context.TODO()), e.opts.Prefix+node.Key(), clientv3.WithFilterPut())
		// Session-scoped recover: a panic must not kill the loop; the outer for
		// re-establishes the watch, mirroring updateLoop.
		func() {
			defer func() {
				if v := recover(); v != nil && e.opts.Log != nil {
					e.opts.Log.ErrorF("Recovered from selfHealLoop with %v, stack: %v", v, process.Stack())
				}
			}()
			for wResp := range wch {
				if wResp.Err() != nil {
					if e.opts.Log != nil {
						e.opts.Log.WarnF("The instance <%v> watch error because of compact: %v", e.opts.Prefix+node.Key(), wResp.Err())
					}
					return
				}
				for _, ev := range wResp.Events {
					if ev.Type != clientv3.EventTypeDelete || ev.Kv == nil {
						continue
					}
					cur := e.instance.Load()
					if cur == nil {
						return // Deregistered: never resurrect
					}
					if string(ev.Kv.Key) != e.opts.Prefix+cur.Key() {
						continue
					}
					leaseID, err := strconv.ParseInt(cur.LeaseId, 16, 64)
					if err != nil {
						continue
					}
					ctx, cancel := context.WithTimeout(context.TODO(), e.opts.DialTimeout)
					_, err = e.opts.Cli.Put(ctx, e.opts.Prefix+cur.Key(), cur.Val(), clientv3.WithLease(clientv3.LeaseID(leaseID)))
					cancel()
					if e.opts.Log != nil {
						if err != nil {
							// The lease is gone (Deregister already revoked it, or it
							// expired and keepaliveLoop's regrant is re-writing the key):
							// safe to ignore — nothing to heal onto a dead lease.
							e.opts.Log.WarnF("The instance <%v> self-heal failed: %v", e.opts.Prefix+cur.Key(), err)
						} else {
							e.opts.Log.InfoF("The instance <%v> self-heal after external deletion", e.opts.Prefix+cur.Key())
						}
					}
				}
			}
		}()
		// Watch closed (disconnect or client close): back off briefly, then
		// re-establish exactly like updateLoop.
		select {
		case <-e.exit:
			return
		case <-time.After(time.Second):
		}
	}
}

// updateLoop keeps the local cache for (prefix, name) in sync with etcd.
// startRev is the revision of the snapshot that already populated the cache
// (GetInstance's Get); the watch replays incrementally from startRev+1 so the
// gap between that Get and the watch-start is covered. etcd's store revision is
// a single cluster-wide counter incremented on every modification, and each
// event's Kv.ModRevision is globally increasing, so tracking the last applied
// ModRevision and resuming from lastRev+1 loses no event across a disconnect.
// If the watched revision was compacted, wResp.Err() fires and we rebuild the
// cache from a fresh consistent Get (resync) and resume from that snapshot's
// revision, so the gap is healed rather than silently diverging. Watching from
// "now" would permanently lose any change during the gap.
func (e *Etcd) updateLoop(prefix, name string, notice func(Action, Node), startRev int64) {
	lastRev := startRev
	var wch clientv3.WatchChan
	for {
		select {
		case <-e.exit:
			return
		default:
		}
		if lastRev > 0 {
			// Resume strictly after the last event we applied, so a disconnect
			// cannot drop intermediate PUT/DELETE changes.
			wch = e.opts.Cli.Watch(clientv3.WithRequireLeader(context.TODO()), prefix+name+defaultKeyPrefix, clientv3.WithPrefix(), clientv3.WithRev(lastRev+1))
		} else {
			wch = e.opts.Cli.Watch(clientv3.WithRequireLeader(context.TODO()), prefix+name+defaultKeyPrefix, clientv3.WithPrefix())
		}
		// Session-scoped recover: a panic (e.g. inside the notice callback) must
		// not terminate this goroutine; the outer for re-establishes the watch.
		func() {
			defer func() {
				if v := recover(); v != nil && e.opts.Log != nil {
					e.opts.Log.ErrorF("Recovered from updateLoop with %v, stack: %v", v, process.Stack())
				}
			}()
			for wResp := range wch {
				if wResp.Err() != nil {
					// e.g. mvcc: required revision has been compacted. Rebuild the
					// cache from a fresh consistent Get and resume from that
					// snapshot's revision, so the gap is healed instead of silently
					// diverging. Watching from "now" (lastRev=0) would permanently
					// lose any change that occurred during the gap.
					if e.opts.Log != nil {
						e.opts.Log.WarnF("The instance <%v> watch error because of compact: %v", prefix+name, wResp.Err())
					}
					if rev, ok := e.resync(prefix, name); ok {
						lastRev = rev
					}
					// resync failure: keep lastRev unchanged so the next iteration
					// retries; once etcd is reachable the Get heals the cache.
					return
				}
				for _, event := range wResp.Events {
					if event.Kv != nil && event.Kv.ModRevision > lastRev {
						lastRev = event.Kv.ModRevision
					}
					switch event.Type {
					case clientv3.EventTypePut:
						if event.Kv == nil {
							continue
						}
						node, err := toNode(event.Kv.Value)
						if err != nil {
							continue
						}
						if event.IsCreate() {
							e.mu.Lock()
							instance := e.nodes[prefix+name]
							if instance == nil {
								e.nodes[prefix+name] = &Instance{Name: node.Name, Nodes: []*Node{node}, Round: 1}
							} else {
								instance.Upsert(node)
							}
							if e.opts.Log != nil {
								e.opts.Log.InfoF("The node <%v> of this instance <%v> have been created", node, prefix+name)
							}
							e.mu.Unlock()
							if notice != nil {
								safeNotice(notice, ActionPut, *node, e.opts.Log)
							}
							continue
						}
						if event.IsModify() {
							e.mu.Lock()
							instance := e.nodes[prefix+name]
							if instance == nil || len(instance.Nodes) == 0 {
								e.nodes[prefix+name] = &Instance{Name: node.Name, Nodes: []*Node{node}, Round: 1}
							} else {
								instance.Upsert(node)
							}
							if e.opts.Log != nil {
								e.opts.Log.InfoF("The node <%v> of this instance <%v> have been updated", node, prefix+name)
							}
							e.mu.Unlock()
							if notice != nil {
								safeNotice(notice, ActionMod, *node, e.opts.Log)
							}
						}
					case clientv3.EventTypeDelete:
						if event.Kv == nil {
							continue
						}
						// The key after TrimPrefix is "<Host>:<Port>". For IPv6 or
						// other colon-containing hosts the first colon is NOT the
						// separator, so split on the LAST colon instead.
						key := strings.TrimPrefix(string(event.Kv.Key), prefix+name+defaultKeyPrefix)
						idx := strings.LastIndex(key, ":")
						if idx < 0 {
							continue
						}
						host, port := key[:idx], key[idx+1:]
						var node *Node
						e.mu.Lock()
						instance := e.nodes[prefix+name]
						if instance != nil {
							node = instance.Delete(host, port)
							// The cache entry lives and dies with its watch: once
							// created it is never removed, so a fully-drained
							// service leaves an empty instance behind (GetInstance
							// reports ErrNodeNotFound via IsEmpty) and no caller can
							// ever see a nil entry to start a second updateLoop for
							// this (prefix, name) — which would double-process every
							// subsequent event and leak the first goroutine.
							if e.opts.Log != nil {
								e.opts.Log.InfoF("The node <%v> of this instance <%v> have been taken offline", string(event.Kv.Key), prefix+name)
							}
						}
						e.mu.Unlock()
						if notice != nil && node != nil {
							safeNotice(notice, ActionDel, *node, e.opts.Log)
						}
					}
				}
			}
		}()
		// watchChan closed (disconnect or client close): back off briefly, then
		// reconnect. If the close was due to a compacted revision, the inner loop
		// already healed the cache via resync; otherwise the next Watch resumes
		// incrementally from lastRev+1. The select also lets Close() exit promptly
		// instead of waiting out the backoff.
		select {
		case <-e.exit:
			return
		case <-time.After(time.Second):
		}
	}
}

// resync rebuilds the cached instance for (prefix, name) from a consistent etcd
// Get and returns the revision the follow-up Watch should resume from (rev+1).
// It is used to heal after a disconnect where the resume revision was compacted
// (or a first watch fails), so the cache is reconciled to current state instead
// of silently diverging. Returns (0, false) if the Get fails, in which case the
// caller keeps its lastRev and retries on the next iteration once etcd is back.
func (e *Etcd) resync(prefix, name string) (int64, bool) {
	ctx, cancel := context.WithTimeout(context.TODO(), e.opts.DialTimeout)
	defer cancel()
	gResp, err := e.opts.Cli.Get(ctx, prefix+name+defaultKeyPrefix, clientv3.WithPrefix(), clientv3.WithSort(clientv3.SortByKey, clientv3.SortDescend))
	if err != nil {
		if e.opts.Log != nil {
			e.opts.Log.WarnF("Resync the instance <%v> failed: %v", prefix+name, err)
		}
		return 0, false
	}
	nodes := make([]*Node, 0, len(gResp.Kvs))
	for _, kv := range gResp.Kvs {
		node, nErr := toNode(kv.Value)
		if nErr != nil {
			continue
		}
		nodes = append(nodes, node)
	}
	e.mu.Lock()
	instance := e.nodes[prefix+name]
	if instance == nil {
		if len(nodes) == 0 {
			e.nodes[prefix+name] = &Instance{Name: name}
		} else {
			e.nodes[prefix+name] = &Instance{Name: name, Nodes: nodes, Round: 1}
		}
	} else {
		instance.Nodes = nodes
		instance.nextRound()
	}
	e.mu.Unlock()
	return gResp.Header.Revision, true
}

// safeNotice guards the user-supplied notice callback so a panic in caller
// code cannot tear down the updateLoop goroutine.
func safeNotice(notice func(Action, Node), action Action, node Node, logger *logger.Logger) {
	defer func() {
		if v := recover(); v != nil && logger != nil {
			logger.ErrorF("Recovered from notice callback with %v, stack: %v", v, process.Stack())
		}
	}()
	notice(action, node)
}
