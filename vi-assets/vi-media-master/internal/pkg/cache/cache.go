package cache

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"github.com/robfig/cron/v3"
	"sync"
	"time"
	"vi-media/internal/conf"
	"vi-media/pkg/ministore"
)

type MultiError []error

func (e MultiError) Error() string {
	var buffer bytes.Buffer
	for _, err := range e {
		buffer.WriteString(err.Error())
		buffer.WriteString(".")
	}
	return buffer.String()
}

const (
	LevelMem  = 0
	LevelDisk = 1

	ZoneC = "ZoneC"
)

// Context 多级缓存上下文
// 同zone名称下的多缓存表组成一个多级缓存
type Context map[string][]*ministore.CacheTable
type StoreContext map[string]string

var cacheCtx Context

func Register(zone string, caches ...*ministore.CacheTable) {
	if cacheCtx == nil {
		cacheCtx = make(map[string][]*ministore.CacheTable, 2)
	}
	cacheList := cacheCtx[zone]
	level := uint8(len(cacheList))
	for _, cache := range caches {
		cache.SetLevel(level)
		cacheList = append(cacheList, cache)
		level++
	}
	cacheCtx[zone] = cacheList
}

func Add(zone, volume, path string, names ...string) error {
	if len(names) == 0 {
		return nil
	}
	cacheList := cacheCtx[zone]
	if len(cacheList) == 0 || cacheList[0] == nil {
		return errors.New(fmt.Sprintf("the %v cache is not exists", zone))
	}

	var mErr MultiError
	for _, name := range names {
		if _, err := cacheList[0].Add(name, path, volume, ""); err != nil {
			mErr = append(mErr, err)
		}
	}
	if mErr != nil {
		return mErr
	}
	return nil
}

func Get(zone string, name string) ([]byte, error) {
	cacheList := cacheCtx[zone]
	if len(cacheList) == 0 {
		return nil, errors.New(fmt.Sprintf("the %v cache is not exists", zone))
	}

	var err error
	var data []byte
	for idx, table := range cacheList {
		data, err = table.GetData(name)
		if err == nil {
			IncDownload(zone, idx)
			return data, nil
		}
	}
	IncDownload(zone, 2)
	return nil, err
}

func Delete(zone string, name string) error {
	cacheList := cacheCtx[zone]
	if len(cacheList) == 0 {
		return errors.New(fmt.Sprintf("the %v cache is not exists", zone))
	}

	for _, table := range cacheList {
		item, _ := table.Delete(name)
		if item != nil {
			break
		}
	}
	return nil
}

func UpdateLifeSpan(zone string, level, lifeSpan int) {
	if level < 0 {
		return
	}
	cacheList := cacheCtx[zone]
	if len(cacheList) == 0 || len(cacheList) <= level {
		return
	}
	cacheCtx[zone][level].SetLifeSpan(time.Duration(lifeSpan) * time.Minute)
}

func UpdateExpiredScanConfig(fetchSize, expiredFetchSize int) {
	ministore.FetchSize = fetchSize
	ministore.ExpiredFetchSize = expiredFetchSize
}

// 过期扫描信号量通道，用于即时扫描
var scanSigns [2]chan struct{}

var waitG sync.WaitGroup
var scheduler *cron.Cron

func Init(ctx context.Context, namespace string) error {
	// 本地缓存初始化
	var err error
	var tables [2]*ministore.CacheTable
	// 原始录音本地缓存初始化
	if tables[0], err = initCache(ZoneC, conf.App.Storage.DB.Mem.Call, conf.App.Storage.Data.Mem.Call, ministore.LifeSpan(time.Duration(conf.App.Storage.Data.Mem.LifeSpan.Call)*time.Minute),
		ministore.ExpireOp(expire)); err != nil {
		return err
	}
	if tables[1], err = initCache(ZoneC, conf.App.Storage.DB.Disk.Call, conf.App.Storage.Data.Disk.Call, ministore.LifeSpan(time.Duration(conf.App.Storage.Data.Disk.LifeSpan.Call)*time.Minute)); err != nil {
		return err
	}
	// 注册到缓存上下文
	Register(ZoneC, tables[0], tables[1])
	// 开启索引重写定时任务
	if err = startCron(); err != nil {
		return err
	}
	// 开启过期扫描
	for i, table := range tables {
		waitG.Add(1)
		scanSigns[i] = make(chan struct{}, 1)
		go func(offset int, cacheTable *ministore.CacheTable) {
			defer waitG.Done()
			cacheTable.ExpiredScan(ctx, scanSigns[offset], ministore.DefaultExpiredScanInterval)
		}(i, table)
	}
	// 注册监控指标
	registerMetrics(namespace)
	return nil
}

func Rewrite(zone string, level int) error {
	cacheList := cacheCtx[zone]
	if len(cacheList) == 0 {
		return errors.New(fmt.Sprintf("the %v cache is not exists", zone))
	}
	return cacheList[level].Rewrite()
}

func DoExpiration(id int) {
	select {
	case scanSigns[id] <- struct{}{}:
	default:
	}
}

func initCache(zone, idxDir, volume string, opts ...ministore.Option) (*ministore.CacheTable, error) {
	opts = append(opts, ministore.LbPolicy(ministore.NewWeightedRoundRobinLBPolicy([]ministore.Volume{{Path: volume, Weight: 1}})))
	cacheTable, err := ministore.NewCacheTable(zone, idxDir, opts...)
	if err != nil {
		if !ministore.IsIndexEmpty(err) {
			return nil, err
		}
		if err = cacheTable.Rebuild(idxDir, volume); err != nil {
			return nil, err
		}
	}
	return cacheTable, nil
}

func Wait() {
	waitG.Wait()
}