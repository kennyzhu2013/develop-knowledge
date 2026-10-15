package registry

import (
	"cmic.chinamobile.com/ziyan/go-sdk/registry"
	"cmic.chinamobile.com/ziyan/go-sdk/version"
	"context"
	"fmt"
	clientv3 "go.etcd.io/etcd/client/v3"
	"strings"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/log"
)

var reg registry.Registry

func Init(cli *clientv3.Client) {
	prefix := "/micro-registry"
	if len(conf.App.Registry.ClusterName) != 0 {
		prefix = "/" + conf.App.Registry.ClusterName + prefix
	}
	reg, _ = registry.NewEtcd(registry.Client(cli), registry.Prefix(prefix), registry.Logger(log.Log))
}

func Register(ctx context.Context) {
	go func() {
		ipAndPort := strings.Split(conf.App.ApiAddress, ":")
		for {
			select {
			case <-ctx.Done():
				return
			default:
				err := reg.Register(registry.Node{
					Id:       conf.App.Registry.AppId,
					Name:     conf.App.Registry.AppName,
					Host:     strings.TrimSpace(ipAndPort[0]),
					Port:     strings.TrimSpace(ipAndPort[1]),
					Status:   registry.NodeStatusUp,
					Weight:   conf.App.Registry.Weight,
					Version:  version.Version,
					Metadata: map[string]any{"Description": "vi media service"},
				})
				if err == nil {
					log.SysInfo("Register on %v success", strings.Join(conf.App.Registry.Address, ","))
					return
				}
				log.SysError("Register on %v failed: %v", strings.Join(conf.App.Registry.Address, ","), err)
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

func Limit(weight int) {
	reg.ChangeNodeWeight(weight)
}

func Status() map[string]any {
	return map[string]any{"id": conf.App.Registry.AppId, "version": version.Version, "weight": reg.NodeWeight(), "status": string(reg.NodeStatus())}
}

func Close() {
	log.SysInfo("Deregister on %v", strings.Join(conf.App.Registry.Address, ","))
	_ = reg.Close()
}

func GetInstance(name string) (*registry.Instance, error) {
	prefix := "/micro-registry"
	if len(conf.App.Registry.ClusterName) != 0 {
		prefix = "/" + conf.App.Registry.ClusterName + prefix
	}
	return reg.GetInstance(name, registry.WithPrefix(prefix), registry.WithStatus(registry.NodeStatusUp))
}

func GetInstanceWithCluster(cluster, name string) (*registry.Instance, error) {
	prefix := "/micro-registry"
	if len(cluster) != 0 {
		prefix = "/" + cluster + prefix
	}
	return reg.GetInstance(name, registry.WithPrefix(prefix), registry.WithStatus(registry.NodeStatusUp))
}

func GetNode(cluster, name, key string, lb LoadBalancer) (string, error) {
	instance, err := GetInstanceWithCluster(cluster, name)
	if err != nil || len(instance.Nodes) == 0 {
		return "", fmt.Errorf("could not find %v app from registry: %v", name, err)
	}
	return lb.GetNode(instance, key)
}