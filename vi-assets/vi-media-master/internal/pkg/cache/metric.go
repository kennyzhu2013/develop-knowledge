package cache

import (
	"cmic.chinamobile.com/ziyan/go-sdk/metric"
	"github.com/prometheus/client_golang/prometheus"
)

var (
	Layers = [2]string{"Mem", "Local"}

	DownloadStatistics *prometheus.CounterVec
)

func CMemSizeGauge() float64 {
	return float64(cacheCtx[ZoneC][0].Size())
}

func CDiskSizeGauge() float64 {
	return float64(cacheCtx[ZoneC][1].Size())
}

func IncDownload(zone string, layer int) {
	DownloadStatistics.WithLabelValues(zone, Layers[layer]).Inc()
}

func registerMetrics(namespace string) {
	DownloadStatistics = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "zone_layer_download",
			Namespace: namespace,
		},
		[]string{"zone", "layer"},
	)
	metric.Register(DownloadStatistics)
	metric.RegisterGauge(namespace, map[string]func() float64{
		"cMem_cache_size": CMemSizeGauge, "cDisk_cache_size": CDiskSizeGauge,
	})
}