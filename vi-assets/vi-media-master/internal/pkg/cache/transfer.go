package cache

import (
	"vi-media/internal/pkg/log"
	"vi-media/pkg/ministore"
)

func expire(cache *ministore.CacheTable, name string) {
	item := cache.Get(name)
	if item == nil {
		return
	}
	caches := cacheCtx[cache.Zone()]
	if len(caches) == 0 || uint8(len(caches)) <= cache.Level()+1 {
		return
	}
	// 数据流转下一层
	nextItem, err := caches[cache.Level()+1].AddAndCopy(item.Name, item.Path, item.Volume, item.Metadata)
	if err != nil {
		if ministore.IsFileNotExists(err) || ministore.IsExists(err) {
			// 文件不存在或已在下层存在，清除本层数据
			_, _ = cache.Delete(name)
			return
		}
		log.WarnF("[%v] Transfer %v to next cache failed: %v", cache.Zone(), item.FullPath(), err)
		return
	}
	log.InfoF("[%v] Transfer %v to next cache, dstPath: %v", cache.Zone(), item.FullPath(), nextItem.FullPath())
	// 清除本层数据
	_, _ = cache.Delete(name)
}