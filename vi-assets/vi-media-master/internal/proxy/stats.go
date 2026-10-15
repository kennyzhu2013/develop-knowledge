package proxy

import "fmt"

type stats struct {
	// The total rtp of the caller
	CallerRtpCnt int
	// The total rtp of the caller in calling
	CallerInCallRtpCnt int
	// The total recording count of the caller
	CallerRecordCnt int
	// The total recording count of the caller in playing
	CallerPlayRecordCnt int
	// The total compensation frame count of the caller in playing
	CallerCompensationFrameCnt int
	// The total rtp of the callee
	CalleeRtpCnt int
	// The total rtp of the callee in calling
	CalleeInCallRtpCnt int
	// The total recording count of the callee
	CalleeRecordCnt int
	// The total recording count of the callee in playing
	CalleePlayRecordCnt int
	// The total compensation frame count of the callee in playing
	CalleeCompensationFrameCnt int
}

func (s *stats) String() string {
	return fmt.Sprintf("Caller: RtpCnt-%v, InCallRtpCnt-%v, RecordCnt-%v, PlayRecordCnt-%v, CompensationFrameCnt-%v, Callee: RtpCnt-%v, InCallRtpCnt-%v, RecordCnt-%v, PlayRecordCnt-%v, CompensationFrameCnt-%v",
		s.CallerRtpCnt,
		s.CallerInCallRtpCnt,
		s.CallerRecordCnt,
		s.CallerPlayRecordCnt,
		s.CallerCompensationFrameCnt,
		s.CalleeRtpCnt,
		s.CalleeInCallRtpCnt,
		s.CalleeRecordCnt,
		s.CalleePlayRecordCnt,
		s.CalleeCompensationFrameCnt,
	)
}

func (s *stats) Reset() {
	s.CallerRtpCnt, s.CalleeRtpCnt = 0, 0
	s.CallerInCallRtpCnt, s.CalleeInCallRtpCnt = 0, 0
	s.CallerRecordCnt, s.CalleeRecordCnt = 0, 0
	s.CallerPlayRecordCnt, s.CalleePlayRecordCnt = 0, 0
	s.CallerCompensationFrameCnt, s.CalleeCompensationFrameCnt = 0, 0
}

func (s *stats) incCallerRtp(session *Session) {
	s.CallerRtpCnt++
	if session.IsInCall() {
		s.CallerInCallRtpCnt++
	}
}

func (s *stats) incCalleeRtp(session *Session) {
	s.CalleeRtpCnt++
	if session.IsInCall() {
		s.CalleeInCallRtpCnt++
	}
}

func (s *stats) incCallerPlayRecord() {
	s.CallerRecordCnt++
	s.CallerPlayRecordCnt++
}

func (s *stats) incCalleePlayRecord() {
	s.CalleeRecordCnt++
	s.CalleePlayRecordCnt++
}