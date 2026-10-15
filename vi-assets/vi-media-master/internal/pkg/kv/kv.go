package kv

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"context"
	clientv3 "go.etcd.io/etcd/client/v3"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/kv/registry"
	"vi-media/internal/pkg/log"
)

var cli *clientv3.Client

func Init() error {
	client, err := clientv3.New(clientv3.Config{Endpoints: conf.App.Registry.Address, DialTimeout: 5 * time.Second})
	if err != nil {
		return err
	}
	registry.Init(client)
	cli = client
	return nil
}

func Watch(ctx context.Context, key string, fn func([]byte) error) {
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			default:
			}
			func() {
				defer func() {
					if v := recover(); v != nil {
						log.WarnF("Recovered from watch with %v, stack: %v", v, process.Stack())
					}
				}()
				log.InfoF("Watch key <%v>", key)
				watchChan := cli.Watch(context.TODO(), key, clientv3.WithPrefix())
				for wResp := range watchChan {
					for _, event := range wResp.Events {
						switch event.Type {
						case clientv3.EventTypePut:
							if event.Kv == nil {
								continue
							}
							if event.IsCreate() || event.IsModify() {
								log.InfoF("The key <%v> have been updated with <%v>", key, string(event.Kv.Value))
								err := fn(event.Kv.Value)
								if err != nil {
									log.WarnF("The kv event <%v-%v> handled failed: %v", string(event.Kv.Key), string(event.Kv.Value), err)
									continue
								}
							}
						case clientv3.EventTypeDelete:
							if event.Kv == nil {
								continue
							}
							log.InfoF("The key <%v> have been struck off", string(event.Kv.Key))
						}
					}
				}
			}()
		}
	}()
}

func Notice(key, val string) error {
	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(5*time.Second))
	defer cancel()
	_, err := cli.Put(ctx, key, val)
	return err
}