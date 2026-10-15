package cache

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"github.com/robfig/cron/v3"
	"path/filepath"
	"time"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/log"
	"vi-media/pkg/cmd"
)

func cacheRewrite() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnF("Recovered from cacheRewrite with %v, stack: %v", v, process.Stack())
		}
	}()
	var err error
	for zone, caches := range cacheCtx {
		for level, cache := range caches {
			log.InfoF("[%v] Cache-%v start the rewrite", zone, level)
			if err = cache.Rewrite(); err != nil {
				log.WarnF("[%v] Cache-%v rewrite failed: %v", zone, level, err)
			}
			log.InfoF("[%v] Cache-%v end the rewrite", zone, level)
		}
	}
}

func dirPurge() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnF("Recovered from dirPurge with %v, stack: %v", v, process.Stack())
		}
	}()
	childDir := filepath.Join("*", time.Now().AddDate(0, 0, -7).Format("2006-01-02"))
	path := filepath.Join(conf.App.Storage.Data.Mem.Call, childDir)
	if _, err := cmd.Exec("rm", "-Rf", path); err != nil {
		log.WarnF("[%v] Directory purge failed: %v", path, err)
	} else {
		log.InfoF("[%v] Directory purge success", path)
	}
	path = filepath.Join(conf.App.Storage.Data.Disk.Call, childDir)
	if _, err := cmd.Exec("rm", "-Rf", path); err != nil {
		log.WarnF("[%v] Directory purge failed: %v", path, err)
	} else {
		log.InfoF("[%v] Directory purge success", path)
	}
}

func startCron() error {
	scheduler = cron.New(cron.WithSeconds())
	if _, err := scheduler.AddFunc(conf.App.Storage.DB.Rewrite, cacheRewrite); err != nil {
		return err
	}
	if _, err := scheduler.AddFunc(conf.App.Storage.Data.Cleanup, dirPurge); err != nil {
		return err
	}
	scheduler.Start()
	return nil
}