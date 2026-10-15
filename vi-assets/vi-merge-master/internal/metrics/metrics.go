package metrics

import (
	"vi-merge/conf"
	"vi-merge/internal/pkg/log"
	"vi-merge/pkg/metric"
)

const (
	NameSpace = "vimerge"
)

func Init() {
	mh := metric.GetDefaultMetricHandler()

	cfg := &metric.MetricConfig{
		Id:        conf.App.HttpAddress,
		Version:   conf.App.Version,
		Namespace: NameSpace,
	}

	if err := mh.Init(cfg); err != nil {
		log.WarnK("10086", "init metric wshandler err: %v", err)
		return
	}

	registerMetrics(mh, NameSpace)

}
