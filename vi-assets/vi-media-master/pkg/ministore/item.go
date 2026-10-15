package ministore

import (
	"path/filepath"
	"time"
)

const KeyVolume = "volume"

type CacheItem struct {
	// 文件名
	Name string
	// 相对子路径
	Path string
	// 存储卷路径
	Volume string
	// 生命周期
	LifeSpan time.Duration

	// 元数据，用于定制化
	Metadata string

	// 创建时间
	CreatedOn time.Time
	// 上一次访问时间
	AccessedOn time.Time
}

func NewCacheItem(name, path, volume string) *CacheItem {
	nowTime := time.Now()
	return &CacheItem{Name: name, Path: path, Volume: volume, CreatedOn: nowTime, AccessedOn: nowTime}
}

func NewCacheItem2(name, path, volume string, lifeSpan time.Duration, metadata string) *CacheItem {
	nowTime := time.Now()
	return &CacheItem{Name: name, Path: path, Volume: volume, LifeSpan: lifeSpan, Metadata: metadata, CreatedOn: nowTime, AccessedOn: nowTime}
}

func (i *CacheItem) FullPath() string {
	return filepath.Join(i.Volume, i.Path, i.Name)
}

func (i *CacheItem) Expired() bool {
	return time.Now().Sub(i.AccessedOn) >= i.LifeSpan
}