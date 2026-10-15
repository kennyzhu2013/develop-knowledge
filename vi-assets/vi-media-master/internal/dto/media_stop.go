package dto

type MediaStop struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// Marked stop media: DTMF、Media
	Mark string `json:"mark" binding:"required,oneof=DTMF Media"`
	// Request sequence number, monotonically increasing
	SeqId uint8 `json:"seqId" binding:"required"`
}