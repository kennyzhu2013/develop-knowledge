package metrics

import (
	"offline-merge/conf"
	"offline-merge/internal/pkg/log"
	"offline-merge/pkg/metric"
)

func Init() {
	namespace := "offlinemerge"
	mh := metric.GetDefaultMetricHandler()
	cfg := &metric.MetricConfig{
		Id:        conf.App.HttpAddress,
		Version:   conf.App.Version,
		Namespace: namespace,
	}

	if err := mh.Init(cfg); err != nil {
		log.WarnK("10086", "init metric handler err: %v", err)
		return
	}

	registerMetrics(mh, namespace)
}
