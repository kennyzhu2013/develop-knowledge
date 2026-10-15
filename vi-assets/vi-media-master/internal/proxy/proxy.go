package proxy

import (
	"cmic.chinamobile.com/ziyan/go-sdk/metric"
	"context"
	"github.com/prometheus/client_golang/prometheus"
	"vi-media/internal/conf"
	"vi-media/internal/pkg/cache"
	"vi-media/internal/pkg/log"
)

const rtpTypeZero = "zero"

var rtpStatistics *prometheus.CounterVec

func Init(ctx context.Context, namespace string) error {
	// Multi-level fileCache
	if err := cache.Init(ctx, namespace); err != nil {
		return err
	}
	log.SysInfo("Init multi-level file cache success")
	// Init avCache and load dudu audio
	if err := initAvCache(ctx); err != nil {
		return err
	}
	log.SysInfo("Init media cache, load dudu audio success")
	// Init session manager
	err := initManager(conf.App.Ims.Start, (conf.App.Ims.End-conf.App.Ims.Start)>>2)
	if err != nil {
		return err
	}
	log.SysInfo("Init session manager success")
	// Register monitor
	metric.RegisterGauge(namespace, map[string]func() float64{"session_active_size": func() float64 {
		return float64(sessionManager.Size())
	}, "audio_cache_size": func() float64 {
		return float64(aCache[aKey].Size())
	}})
	rtpStatistics = prometheus.NewCounterVec(prometheus.CounterOpts{Name: "rtp", Namespace: namespace}, []string{"rType"})
	metric.Register(rtpStatistics)
	return nil
}

func incRTP(rType string) {
	rtpStatistics.WithLabelValues(rType).Inc()
}

func Stop() {
	sessionManager.Stop()
}

func Wait() {
	cache.Wait()
}