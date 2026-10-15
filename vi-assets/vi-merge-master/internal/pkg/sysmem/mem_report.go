package sysmem

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

type MemInfo struct {
	MemTotal     uint64 // 总内存
	MemFree      uint64 // 空闲内存
	MemAvailable uint64 // 可用内存(应用可用，含缓存回收)
	Buffers      uint64
	Cached       uint64
}

type MemInfoCache struct {
	mu         sync.RWMutex
	cache      *MemInfo
	lastUpdate time.Time
	expireDur  time.Duration // 过期间隔，例如5 * time.Second
}

// NewMemInfoCache 构造缓存，xSec 间隔分钟
func NewMemInfoCache(xSec int) *MemInfoCache {
	return &MemInfoCache{
		expireDur: time.Duration(xSec) * time.Second,
	}
}

// Get 获取缓存，超时自动刷新
func (c *MemInfoCache) Get() (*MemInfo, error) {
	// 先读锁快速读取
	c.mu.RLock()
	now := time.Now()
	expired := now.Sub(c.lastUpdate) >= c.expireDur
	if !expired {
		res := c.cache
		c.mu.RUnlock()
		return res, nil
	}
	c.mu.RUnlock()

	// 过期，加写锁刷新
	c.mu.Lock()
	defer c.mu.Unlock()

	// 防止并发争抢已经别的goroutine刷新完毕，二次检查
	if now.Sub(c.lastUpdate) < c.expireDur {
		return c.cache, nil
	}

	newInfo, err := GetHostMemInfo()
	if err != nil {
		// 获取失败：可选择返回旧缓存或者返回error
		return c.cache, err
	}

	c.cache = newInfo
	c.lastUpdate = now
	return newInfo, nil
}

func GetHostMemInfo() (*MemInfo, error) {
	f, err := os.Open("/proc/meminfo")
	if err != nil {
		return nil, err
	}
	defer f.Close()

	info := new(MemInfo)
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := scanner.Text()
		parts := strings.Fields(line)
		if len(parts) < 2 {
			continue
		}
		key := parts[0]
		val, _ := strconv.ParseUint(parts[1], 10, 64)

		switch key {
		case "MemTotal:":
			info.MemTotal = val
		case "MemFree:":
			info.MemFree = val
		case "MemAvailable:":
			info.MemAvailable = val
		case "Buffers:":
			info.Buffers = val
		case "Cached:":
			info.Cached = val
		}
	}
	return info, scanner.Err()
}

func GetHostMemText() (string, error) {
	info, err := GetHostMemInfo()
	if err != nil {
		return "", err
	}
	total, free, avail := info.ToMB()
	menInfo := fmt.Sprintf("MemTotal:%v,MemFree=%v,MemAvailable=%v,usedPercent=%v", total, free, avail, info.UsedPercent())
	return menInfo, nil
}

// ToMB KB转MB
func (m *MemInfo) ToMB() (total, free, avail uint64) {
	return m.MemTotal / 1024, m.MemFree / 1024, m.MemAvailable / 1024
}

func (m *MemInfo) ToGB() (total, free, avail uint64) {
	oneGMB := uint64(1024 * 1024)
	return m.MemTotal / oneGMB, m.MemFree / oneGMB, m.MemAvailable / oneGMB
}

// UsedPercent 内存使用率 0~100
func (m *MemInfo) UsedPercent() float64 {
	if m.MemTotal == 0 {
		return 0
	}
	used := m.MemTotal - m.MemAvailable
	return float64(used) * 100 / float64(m.MemTotal)
}
