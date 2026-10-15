package metrics

import (
	"offline-merge/pkg/metric"

	"github.com/prometheus/client_golang/prometheus"
)

var (
	// entry stage
	MergeFailCount   *prometheus.CounterVec
	MergeProcessTime *prometheus.HistogramVec

	// receive stage
	MqReceiveCount        *prometheus.CounterVec
	MQMessageInvalidCount *prometheus.CounterVec //new

	// download stage
	DownFileFailCounter *prometheus.CounterVec
	MergeDownTime       *prometheus.HistogramVec

	// ffmpeg command stage
	FFCmdFailCounter *prometheus.CounterVec //new

	FfRunFailCounter      *prometheus.CounterVec
	StartTimeWrongCounter *prometheus.CounterVec
	FileRwErrCounter      *prometheus.CounterVec
	FileLocalSaveCounter  *prometheus.CounterVec
	FallbackCounter       *prometheus.CounterVec

	MergeLegDifTime *prometheus.HistogramVec

	// upload stage
	UploadFailCount *prometheus.CounterVec
	MergeUploadTime *prometheus.HistogramVec

	// bill stage
	Send2BillFailCount *prometheus.CounterVec
)

const (
	DefaultMode = "unknown"
)

func MqReceiverInc(mode string) {
	if MqReceiveCount != nil {
		MqReceiveCount.WithLabelValues(mode).Inc()
	}
}

func MQMessageInvalidInc(mode string) {
	if MQMessageInvalidCount != nil {
		MQMessageInvalidCount.WithLabelValues(mode).Inc()
	}
}

func MergeFailInc(mode string) {
	if MergeFailCount != nil {
		MergeFailCount.WithLabelValues(mode).Inc()
	}
}

func Send2BillFailInc(mode string) {
	if Send2BillFailCount != nil {
		Send2BillFailCount.WithLabelValues(mode).Inc()
	}
}

func FFCmdFailInc(mode string, reason string) {
	if FFCmdFailCounter != nil {
		FFCmdFailCounter.WithLabelValues(mode, reason).Inc()
	}
}

func UploadFailInc(mode string, reason string) {
	if UploadFailCount != nil {
		UploadFailCount.WithLabelValues(mode, reason).Inc()
	}
}

func DownFileFailInc(mode string, reason string) {
	if DownFileFailCounter != nil {
		DownFileFailCounter.WithLabelValues(mode, reason).Inc()
	}
}

func FfRunFailInc(mode string) {
	if FfRunFailCounter != nil {
		FfRunFailCounter.WithLabelValues(mode).Inc()
	}
}

func StartTimeWrongInc(mode string) {
	if StartTimeWrongCounter != nil {
		StartTimeWrongCounter.WithLabelValues(mode).Inc()
	}
}

func FileRwErrInc(mode string) {
	if FileRwErrCounter != nil {
		FileRwErrCounter.WithLabelValues(mode).Inc()
	}
}

func FileLocalSaveInc(mode string) {
	if FileLocalSaveCounter != nil {
		FileLocalSaveCounter.WithLabelValues(mode).Inc()
	}
}

func FallbackInc(mode string) {
	if FallbackCounter != nil {
		FallbackCounter.WithLabelValues(mode).Inc()
	}
}

func MergeTimeObserve(mode string, time float64) {
	if MergeProcessTime != nil {
		MergeProcessTime.WithLabelValues(mode).Observe(time)
	}
}

func MergeDownTimeObserve(mode string, time float64) {
	if MergeDownTime != nil {
		MergeDownTime.WithLabelValues(mode).Observe(time)
	}
}

func MergeUploadTimeObserve(mode string, time float64) {
	if MergeUploadTime != nil {
		MergeUploadTime.WithLabelValues(mode).Observe(time)
	}
}

func MergeLegDiffTimeObserve(mode string, time float64) {
	if MergeLegDifTime != nil {
		MergeLegDifTime.WithLabelValues(mode).Observe(time)
	}
}

func registerMetrics(mh *metric.MetricHandler, namespace string) {
	MqReceiveCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "mq_receive_count",
			Namespace: namespace,
			Help:      "received merge message mq count",
		},
		[]string{"mode"},
	)

	MQMessageInvalidCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "mq_message_invalid_count",
			Namespace: namespace,
			Help:      "invalid mq message count",
		},
		[]string{"mode"},
	)

	MergeFailCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_fail_count",
			Namespace: namespace,
			Help:      "count of merge fail in any stage",
		},
		[]string{"mode"},
	)

	Send2BillFailCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_bill_fail_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	DownFileFailCounter = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_down_fail_count",
			Namespace: namespace,
		},
		[]string{"mode", "reason"},
	)

	UploadFailCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_upload_fail_count",
			Namespace: namespace,
		},
		[]string{"mode", "reason"},
	)

	FfRunFailCounter = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_ffrun_fail_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	FFCmdFailCounter = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_ff_command_fail_count",
			Namespace: namespace,
			Help:      "count when execute FFMpeg command failed",
		},
		[]string{"mode", "reason"},
	)

	StartTimeWrongCounter = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_starttime_wrong_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	FileRwErrCounter = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_filerw_err_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	FileLocalSaveCounter = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_file_local_save_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	FallbackCounter = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name:      "merge_fallback_count",
			Namespace: namespace,
		},
		[]string{"mode"},
	)

	//metric.Register(MqReceiveCount, MergeFailCount)
	mh.RegisterCollectors(MqReceiveCount, MQMessageInvalidCount, MergeFailCount, Send2BillFailCount, DownFileFailCounter, UploadFailCount,
		FfRunFailCounter, FFCmdFailCounter, StartTimeWrongCounter, FileRwErrCounter, FileLocalSaveCounter, FallbackCounter)

	MergeProcessTime = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Namespace: namespace,
			Name:      "merge_total_time",
			Help:      "interval of entry process",
		},
		[]string{"mode"},
	)

	MergeDownTime = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Namespace: namespace,
			Name:      "merge_down_time",
		},
		[]string{"mode"},
	)

	MergeUploadTime = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Namespace: namespace,
			Name:      "merge_upload_time",
		},
		[]string{"mode"},
	)

	MergeLegDifTime = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Namespace: namespace,
			Name:      "merge_leg_diff_time",
		},
		[]string{"mode"},
	)

	mh.RegisterHistogramVecs(MergeProcessTime, MergeDownTime, MergeUploadTime, MergeLegDifTime)

	//metric.RegisterGauge(namespace, map[string]func() float64{
	//	"eMem_cache_size": EMemSizeGauge, "cMem_cache_size": CMemSizeGauge, "mMem_cache_size": MMemSizeGauge,
	//	"eDisk_cache_size": EDiskSizeGauge, "cDisk_cache_size": CDiskSizeGauge, "mDisk_cache_size": MDiskSizeGauge,
	//	"pending_store_queue_size": StoreQueueSizeGauge, "upload_active_size": UploadActiveSizeGauge,
	//})
}
