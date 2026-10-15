package monitor

import (
	"context"
	"github.com/shirou/gopsutil/v4/cpu"
	"time"
	"vi-media/internal/conf"
)

type State uint8

const (
	Normal   State = 0
	Overload State = 1
)

func Run(ctx context.Context, handler func(State)) {
	ticker := time.NewTicker(time.Duration(conf.App.Monitor.CPU.Tick) * time.Second)
	cpuStat := func() {
		busy := false
		for {
			select {
			case <-ctx.Done():
				ticker.Stop()
				return
			case <-ticker.C:
				stat, err := cpu.PercentWithContext(ctx, time.Duration(conf.App.Monitor.CPU.Interval)*time.Second, false)
				if err != nil || len(stat) == 0 {
					continue
				}
				if stat[0] >= conf.App.Monitor.CPU.Threshold {
					if !busy {
						busy = true
						handler(Overload)
					}
					continue
				}
				if busy {
					busy = false
					handler(Normal)
				}
			}
		}
	}
	go cpuStat()
}