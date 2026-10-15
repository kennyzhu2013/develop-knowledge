package metric

import (
	"errors"
	"fmt"
	"sync/atomic"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/collectors"
)

/*
prometheus metric priority:
1 collector->collectorFunc,
2.every item follows sequence which registered
*/

// singleton mode, provide unique interface to get
var defaultMetricHandler MetricHandler

func GetDefaultMetricHandler() *MetricHandler {
	return &defaultMetricHandler
}

type MetricHandler struct {
	initialized atomic.Bool

	namespace string
}

func (m *MetricHandler) Init(cfg *MetricConfig) error {
	if m.getInitialized() {
		return errors.New("metric wshandler already initialized")
	}

	if cfg == nil {
		return errors.New("config is nil")
	}

	m.namespace = cfg.Namespace

	if cfg.Labels == nil {
		cfg.Labels = make(map[string]string)
	}

	//now add in the back.todo:put id and version in the front
	cfg.Labels[fmt.Sprintf("%v_%v", cfg.Namespace, "id")] = cfg.Id
	cfg.Labels[fmt.Sprintf("%v_%v", cfg.Namespace, "version")] = cfg.Version

	registry := prometheus.NewRegistry()
	prometheus.DefaultGatherer = registry
	prometheus.DefaultRegisterer = prometheus.WrapRegistererWith(cfg.Labels, registry)

	//register default system collector,like process,go running time
	prometheus.DefaultRegisterer.MustRegister(
		collectors.NewProcessCollector(collectors.ProcessCollectorOpts{Namespace: m.namespace}),
		collectors.NewGoCollector(),
	)

	m.setInitialized(true)

	return nil
}

func (m *MetricHandler) RegisterCollectors(collectors ...prometheus.Collector) {
	for _, collector := range collectors {
		_ = prometheus.DefaultRegisterer.Register(collector)
	}
}

func (m *MetricHandler) RegisterHistogramVecs(collectors ...*prometheus.HistogramVec) {
	for _, collector := range collectors {
		_ = prometheus.DefaultRegisterer.Register(collector)
	}
}

func (m *MetricHandler) RegisterGaugeFunc(fns GaugeFunctionsType) {
	for name, fn := range fns {
		_ = prometheus.DefaultRegisterer.Register(prometheus.NewGaugeFunc(
			prometheus.GaugeOpts{
				Name:      name,
				Namespace: m.namespace,
			},
			fn,
		))
	}
}

func (m *MetricHandler) setInitialized(f bool) {
	m.initialized.Store(f)
}
func (m *MetricHandler) getInitialized() bool {
	f := m.initialized.Load()
	return f
}
