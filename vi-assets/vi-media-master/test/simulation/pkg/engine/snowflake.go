package engine

import (
	"fmt"
	"sync"
	"time"
)

const (
	MachineId    = 31
	DataCenterId = 3
)

const (
	startTimestamp int64 = 1561690135700

	bitSeq        int64 = 12
	bitMachine    int64 = 7
	bitDataCenter int64 = 2

	maxSeq        int64 = -1 ^ (-1 << bitSeq)
	maxMachine    int64 = -1 ^ (-1 << bitMachine)
	maxDataCenter int64 = -1 ^ (-1 << bitDataCenter)

	offsetMachine    = bitSeq
	offsetDataCenter = bitSeq + bitMachine
	offsetTimestamp  = offsetDataCenter + bitDataCenter
)

type Snowflake struct {
	curSeq       int64
	machineId    int64
	dataCenterId int64
	sync.Mutex
}

func NewSnowflake(machineId, dataCenterId int64) (*Snowflake, error) {
	if machineId > maxMachine || machineId < 0 {
		return nil, fmt.Errorf("the machineId is invalid, expect: 0 < machineId < %v", maxMachine)
	}
	if dataCenterId > maxDataCenter || dataCenterId < 0 {
		return nil, fmt.Errorf("the dataCenterId is invalid, expect: 0 < dataCenterId < %v", maxDataCenter)
	}
	return &Snowflake{curSeq: -1, machineId: machineId, dataCenterId: dataCenterId}, nil
}

func (s *Snowflake) Next() int64 {
	s.Lock()
	defer s.Unlock()
	s.curSeq = (s.curSeq + 1) & maxSeq
	return (((time.Now().UnixMilli() - startTimestamp) / 100) << offsetTimestamp) | s.dataCenterId<<offsetDataCenter | s.machineId<<offsetMachine | s.curSeq
}