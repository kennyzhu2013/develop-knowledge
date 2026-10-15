package metrics

import (
	"vi-merge/internal/session"
	"vi-merge/pkg/metric"

	"github.com/prometheus/client_golang/prometheus"
)

var (
	CallFragmentCount  *prometheus.CounterVec
	CallSourceCount    *prometheus.CounterVec
	Send2BillFailCount *prometheus.CounterVec
	UploadFailCount    *prometheus.CounterVec

	MergeWsFailCount      *prometheus.CounterVec
	Pcm2MixChanFullCount  *prometheus.CounterVec
	FileNodeNotFoundCount *prometheus.CounterVec
	SaveLocalDiskCount    *prometheus.CounterVec
	Buffer2FileCount      *prometheus.CounterVec

	StreamMergeUploadTime *prometheus.HistogramVec
)

func CallFragmentInc(mode string) {
	if CallFragmentCount != nil {
		CallFragmentCount.WithLabelValues(mode).Inc()
	}
}

func CallSourceInc(mode string, source string) {
	if CallSourceCount != nil {
		CallSourceCount.WithLabelValues(mode, source).Inc()
	}
}

func Send2BillFailInc(mode string) {
	if Send2BillFailCount != nil {
		Send2BillFailCount.WithLabelValues(mode).Inc()
	}
}

func UploadFailInc(mode string) {
	if UploadFailCount != nil {
		UploadFailCount.WithLabelValues(mode).Inc()
	}
}

func MergeWsFailInc(mode string, reason string) {
	if MergeWsFailCount != nil {
		MergeWsFailCount.WithLabelValues(mode, reason).Inc()
	}
}

func Pcm2MixChanFullInc(mode string) {
	if Pcm2MixChanFullCount != nil {
		Pcm2MixChanFullCount.WithLabelValues(mode).Inc()
	}
}

func FileNodeNotFoundInc(mode string) {
	if FileNodeNotFoundCount != nil {
		FileNodeNotFoundCount.WithLabelValues(mode).Inc()
	}
}

func SaveLocalDiskInc(mode string) {
	if SaveLocalDiskCount != nil {
		SaveLocalDiskCount.WithLabelValues(mode).Inc()
	}
}

func Buffer2FileInc(mode string) {
	if Buffer2FileCount != nil {
		Buffer2FileCount.WithLabelValues(mode).Inc()
	}
}

func StreamMergeUploadTimeObserve(mode string, time float64) {
	if StreamMergeUploadTime != nil {
		StreamMergeUploadTime.WithLabelValues(mode).Observe(time)
	}
}

func registerMetrics(mh *metric.MetricHandler, namespace string) {
	CallFragmentCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_merge_fragment_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	CallSourceCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_merge_source_count",
			Namespace: namespace,
		},
		[]string{"mode", "source"},
	)

	Send2BillFailCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_merge_bill_fail_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	UploadFailCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_merge_upload_fail_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	MergeWsFailCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_merge_ws_fail_count",
			Namespace: namespace,
		},
		[]string{"mode", "reason"},
	)

	Pcm2MixChanFullCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_pcmmix_chanfull_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	FileNodeNotFoundCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_node_notfound_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	SaveLocalDiskCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_save_disk_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	Buffer2FileCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "stream_buffer2file_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	StreamMergeUploadTime = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Namespace: namespace,
			Name:      "stream_merge_upload_time",
		},
		[]string{"mode"},
	)

	mh.RegisterCollectors(CallFragmentCount, CallSourceCount, Send2BillFailCount, UploadFailCount,
		MergeWsFailCount, Pcm2MixChanFullCount, FileNodeNotFoundCount, SaveLocalDiskCount, Buffer2FileCount)

	mh.RegisterHistogramVecs(StreamMergeUploadTime)

	mh.RegisterGaugeFunc(metric.GaugeFunctionsType{
		"stream_merge_active_session": GetActiveSessionSize,
	})

}

func GetActiveSessionSize() float64 {
	return float64(session.GetConnManager().GetSize())
}
