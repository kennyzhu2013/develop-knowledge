package metric

import (
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/collectors"
	"net/http"
)

var defaultMonitor Monitor
var DefaultMonitor = &defaultMonitor

type Monitor struct {
	// 指标收集器
	cVec []*prometheus.CounterVec
	sVec []*prometheus.SummaryVec
	hVec []*prometheus.HistogramVec
	// 标签值抽取函数
	extract func(*gin.Context) []string
}

func New(id, version, namespace string, opt ...Option) *Monitor {
	m := Monitor{}
	// 加载元数据
	opts := new(Options)
	for _, o := range opt {
		o(opts)
	}
	metaData := make(map[string]string, 2+len(opts.MetaData))
	for k, v := range opts.MetaData {
		metaData[k] = v
	}
	metaData[fmt.Sprintf("%v_%v", namespace, "id")] = id
	metaData[fmt.Sprintf("%v_%v", namespace, "version")] = version
	// 初始并注册prometheus
	registry := prometheus.NewRegistry()
	prometheus.DefaultGatherer = registry
	prometheus.DefaultRegisterer = prometheus.WrapRegistererWith(metaData, registry)
	prometheus.DefaultRegisterer.MustRegister(
		collectors.NewProcessCollector(collectors.ProcessCollectorOpts{Namespace: namespace}),
		collectors.NewGoCollector(),
	)
	// 统一监控指标
	for name, labelNames := range opts.CounterVec {
		vec := prometheus.NewCounterVec(
			prometheus.CounterOpts{
				Name:      name,
				Namespace: namespace,
			},
			labelNames,
		)
		_ = prometheus.DefaultRegisterer.Register(vec)
		m.cVec = append(m.cVec, vec)
	}
	for name, item := range opts.ObserveVec {
		if item.OType == Summary {
			vec := prometheus.NewSummaryVec(
				prometheus.SummaryOpts{
					Name:      name,
					Namespace: namespace,
				},
				item.Names,
			)
			_ = prometheus.DefaultRegisterer.Register(vec)
			m.sVec = append(m.sVec, vec)
		} else if item.OType == Histogram {
			vec := prometheus.NewHistogramVec(
				prometheus.HistogramOpts{
					Namespace: namespace,
					Name:      name,
				},
				item.Names,
			)
			_ = prometheus.DefaultRegisterer.Register(vec)
			m.hVec = append(m.hVec, vec)
		}
	}
	m.extract = opts.ExtractFunc
	return &m
}

func Actuator(ctx *gin.Context) {
	DefaultMonitor.Serve(ctx)
}

func Register(collectors ...prometheus.Collector) {
	for _, collector := range collectors {
		_ = prometheus.DefaultRegisterer.Register(collector)
	}
}

func RegisterGauge(namespace string, fns map[string]func() float64) {
	for name, fn := range fns {
		_ = prometheus.DefaultRegisterer.Register(prometheus.NewGaugeFunc(
			prometheus.GaugeOpts{
				Name:      name,
				Namespace: namespace,
			},
			fn,
		))
	}
}

func (m *Monitor) Serve(ctx *gin.Context) {
	if m == nil || m.extract == nil {
		ctx.Next()
		return
	}

	// 抽取目标标签值
	lvs := m.extract(ctx)
	if len(lvs) == 0 {
		ctx.Next()
		return
	}

	// 时延监控
	var timer *prometheus.Timer
	if len(m.sVec) != 0 || len(m.hVec) != 0 {
		timer = prometheus.NewTimer(prometheus.ObserverFunc(func(v float64) {
			for _, s := range m.sVec {
				s.WithLabelValues(lvs...).Observe(v)
			}
			for _, h := range m.hVec {
				h.WithLabelValues(lvs...).Observe(v)
			}
		}))
	}

	// 主业务
	ctx.Next()

	if timer != nil {
		timer.ObserveDuration()
	}
	if len(m.cVec) != 0 {
		if ctx.Writer.Status() < http.StatusMultipleChoices {
			lvs = append(lvs, "success")
			for _, c := range m.cVec {
				c.WithLabelValues(lvs...).Inc()
			}
		} else {
			lvs = append(lvs, "fail")
			for _, c := range m.cVec {
				c.WithLabelValues(lvs...).Inc()
			}
		}
	}
}
