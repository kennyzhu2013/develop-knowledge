package notify

type MediaInspectNotice struct {
	// Session unique ID
	SessionId string `json:"sessionId" binding:"required"`
	// The detail of the event
	Message string `json:"message" binding:"required"`
	// The event name: EXCEPTION
	Event string `json:"event" binding:"required,oneof=EXCEPTION TERMINATION"`
	// The event data
	Data interface{} `json:"data"`
}
