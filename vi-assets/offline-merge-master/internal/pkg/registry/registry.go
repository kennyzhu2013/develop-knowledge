package registry

import (
	"context"
	"errors"
	"fmt"
	"offline-merge/conf"
	"offline-merge/internal/pkg/log"
	"offline-merge/pkg/registry"
	"offline-merge/pkg/version"
	"strconv"
	"strings"
	"sync"
	"time"
)

var reg registry.Registry

func Init(ctx context.Context) {
	prefix := "/micro-registry"
	if len(conf.App.Registry.ClusterName) != 0 {
		prefix = "/" + conf.App.Registry.ClusterName + prefix
	}
	reg, _ = registry.NewEtcd(registry.Endpoints(conf.App.Registry.Address), registry.Prefix(prefix), registry.Logger(log.Log))
	// Start registering
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			default:
				err := reg.Register(registry.Node{
					Id:       conf.App.Registry.ServerId,
					Name:     conf.App.Registry.ServerName,
					Host:     conf.App.Host,
					Port:     strconv.Itoa(conf.App.Port),
					Status:   registry.NodeStatusUp,
					Version:  version.Version,
					Metadata: map[string]any{"Description": "offline merge service"},
				})
				if err == nil {
					log.SysInfo("Register on %v success", strings.Join(conf.App.Registry.Address, ","))
					return
				}
				time.Sleep(3 * time.Second)
			}
		}
	}()
}

func TurnUp() {
	reg.ChangeNodeStatus(registry.NodeStatusUp)
}

func TurnMaintenance() {
	reg.ChangeNodeStatus(registry.NodeStatusMaintenance)
}

func TurnOverloaded() {
	reg.ChangeNodeStatus(registry.NodeStatusOverloaded)
}

func Status() map[string]any {
	return map[string]any{"id": conf.App.Registry.ServerId, "version": version.Version, "state": string(reg.NodeStatus())}
}

func Close() {
	log.SysInfo("Deregister on %v", strings.Join(conf.App.Registry.Address, ","))
	_ = reg.Close()
}

func GetInstance(clusterName, name string) (*registry.Instance, error) {
	prefix := "/micro-registry"
	if len(clusterName) != 0 {
		prefix = "/" + clusterName + prefix
	}
	return reg.GetInstance(name, registry.WithPrefix(prefix), registry.WithStatus(registry.NodeStatusUp))
}

//------------------------

var (
	index = -1
	mu    sync.Mutex

	innerIndex = -1
	innerMu    sync.Mutex
)

func GetNode(clusterName, appName string) (string, error) {
	instance, err := GetInstance(clusterName, appName)
	if err != nil || len(instance.Nodes) == 0 {
		return "", errors.New(fmt.Sprintf("could not find %v app from registry: %v", appName, err))
	}
	mu.Lock()
	defer mu.Unlock()
	index++
	if index >= len(instance.Nodes) {
		index = 0
	}
	return fmt.Sprintf("%v:%v", instance.Nodes[index].Host, instance.Nodes[index].Port), nil
}

func GetInnerNode(clusterName, appName string) (string, error) {
	instance, err := GetInstance(clusterName, appName)
	if err != nil || len(instance.Nodes) == 0 {
		return "", errors.New(fmt.Sprintf("could not find %v app from registry: %v", appName, err))
	}
	innerMu.Lock()
	defer innerMu.Unlock()
	innerIndex++
	if innerIndex >= len(instance.Nodes) {
		innerIndex = 0
	}
	return fmt.Sprintf("%v:%v", instance.Nodes[innerIndex].InnerHost, instance.Nodes[innerIndex].InnerPort), nil
}
