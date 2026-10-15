package record

import (
	"path/filepath"
	"strconv"
	"time"
)

func IsSupportedAudio(mimeType string) bool {
	return mimeType == MimeTypeAmrNB || mimeType == MimeTypeAmrNBOctet || mimeType == MimeTypeAmrWB || mimeType == MimeTypeAmrWBOctet ||
		mimeType == MimeTypePCMA || mimeType == MimeTypePCMU || mimeType == MimeTypeG729
}

func IsSupportedVideo(mimeType string) bool {
	return mimeType == MimeTypeLowH264 || mimeType == MimeTypeMidH264 || mimeType == MimeTypeHigH264 || mimeType == MimeTypeExtH264
}

func New(mimeType, filename string, opt ...Option) (AVMedia, error) {
	// Generate path
	opts := &Options{}
	for _, o := range opt {
		o(opts)
	}
	path, timestamp := GeneratePath(opts)
	// Create
	var err error
	var avMedia AVMedia
	switch mimeType {
	case MimeTypeAmrNB:
		avMedia, err = NewAmr(opts.InMemory, opts.NonDivided, ModeBandwidthEfficient, MimeTypeAmrNB, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixAmr))
	case MimeTypeAmrNBOctet:
		avMedia, err = NewAmr(opts.InMemory, opts.NonDivided, ModeOctetAligned, MimeTypeAmrNB, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixAmr))
	case MimeTypeAmrWB:
		avMedia, err = NewAmr(opts.InMemory, opts.NonDivided, ModeBandwidthEfficient, MimeTypeAmrWB, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixAmr))
	case MimeTypeAmrWBOctet:
		avMedia, err = NewAmr(opts.InMemory, opts.NonDivided, ModeOctetAligned, MimeTypeAmrWB, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixAmr))
	case MimeTypePCMA:
		avMedia, err = NewG711(opts.InMemory, opts.NonDivided, MimeTypePCMA, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixWav))
	case MimeTypePCMU:
		avMedia, err = NewG711(opts.InMemory, opts.NonDivided, MimeTypePCMU, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixWav))
	case MimeTypeG729:
		avMedia, err = NewG729(opts.InMemory, opts.NonDivided, MimeTypeG729, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixG729))
	case MimeTypeLowH264:
		avMedia, err = NewH264(opts.NonDivided, MimeTypeLowH264, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixH264))
	case MimeTypeMidH264:
		avMedia, err = NewH264(opts.NonDivided, MimeTypeMidH264, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixH264))
	case MimeTypeHigH264:
		avMedia, err = NewH264(opts.NonDivided, MimeTypeHigH264, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixH264))
	case MimeTypeExtH264:
		avMedia, err = NewH264(opts.NonDivided, MimeTypeExtH264, opts.Volume, path, GenerateName(opts.NonDivided, filename, timestamp, SuffixH264))
	}
	return avMedia, err
}

func GeneratePath(opts *Options) (string, string) {
	// 时间戳转换
	nowTime := time.Now()
	if opts.TimeStamp != 0 {
		nowTime = time.Unix(opts.TimeStamp, 0)
	}
	// 非划分目录
	if opts.NonDivided {
		return "", nowTime.Format("20060102150405")
	}
	// 划分目录
	path := filepath.Join(opts.GId, nowTime.Format("2006-01-02"), strconv.FormatInt(opts.TimeStamp, 10))
	return path, nowTime.Format("20060102150405")
}

func GenerateName(simple bool, filename, timestamp, suffix string) string {
	if simple {
		return filename + suffix
	}
	if len(timestamp) != 0 {
		filename += "_" + timestamp
	}
	return filename + suffix
}