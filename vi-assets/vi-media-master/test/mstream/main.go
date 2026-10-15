package main

import (
	"fmt"
	"github.com/bytedance/sonic"
	"github.com/gorilla/websocket"
	"net/http"
	"time"
)

type Event string

type Notice struct {
	// "FRAME"，"CALLING"，"CODEC_SWITCH"，"END"
	Event Event `json:"event"`
	// 数据
	Data interface{} `json:"data"`
}

const EventEnd Event = "END"

func main() {
	u := &websocket.Upgrader{HandshakeTimeout: 5 * time.Second}
	http.HandleFunc("/media/stream", func(writer http.ResponseWriter, request *http.Request) {
		conn, err := u.Upgrade(writer, request, nil)
		if err != nil {
			writer.WriteHeader(http.StatusNotAcceptable)
			return
		}

		defer func() {
			_ = conn.WriteControl(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""), time.Now().Add(500*time.Millisecond))
			_ = conn.Close()
		}()

		var sum int
		var data []byte
		var messageType int
		var notice Notice
		for {
			messageType, data, err = conn.ReadMessage()
			if err != nil {
				fmt.Printf("Sum: %v, err: %v\n", sum, err)
				break
			}
			if messageType == websocket.TextMessage {
				_ = sonic.Unmarshal(data, &notice)
				if notice.Event == EventEnd {
					fmt.Printf("Sum: %v, err: %v\n", sum, err)
					break
				}
				continue
			}
			sum++
		}
	})
	http.HandleFunc("/media/notice", func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusOK)
	})
	_ = http.ListenAndServe("10.34.108.29:9988", nil)
}