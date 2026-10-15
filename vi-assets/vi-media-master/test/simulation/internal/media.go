package internal

import (
	"fmt"
	"net"
	"simulation/pkg/engine"
	"simulation/pkg/rtp"
	"sync"
	"time"
)

const (
	TrackCaller = "TrackCaller"
	TrackCallee = "TrackCallee"
)

var (
	mutePcma = []byte{
		213, 85, 213, 85, 213, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213,
		85, 213, 213, 85, 213, 85, 213, 85, 213, 213, 213, 213, 85, 213, 85, 213, 85, 85, 213, 85, 213,
		85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213,
		85, 213, 85, 213, 85, 213, 85, 85, 213, 85, 213, 85, 213, 85, 213, 85, 213, 85, 85, 213, 85, 213,
		85, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213,
		213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213, 85,
		85, 213, 85, 213, 213, 213, 85, 213, 85, 213, 213, 213, 85, 213, 213, 85, 213, 213, 85, 213, 85,
		213, 213, 85, 213, 85, 213, 85, 213, 213, 85, 213,
	}
	mutePcmu = []byte{
		255, 126, 255, 126, 255, 255, 126, 255, 255, 126, 255, 126, 255, 126, 254, 126, 254, 126, 255, 255, 126, 255, 255,
		126, 255, 126, 255, 126, 255, 255, 255, 255, 126, 255, 126, 254, 126, 126, 254, 126, 254, 126, 255, 255, 126, 254,
		126, 254, 126, 254, 126, 255, 255, 126, 254, 126, 254, 126, 255, 255, 126, 254, 125, 254, 125, 254, 126, 254, 126,
		126, 254, 126, 255, 126, 255, 126, 254, 126, 254, 126, 126, 254, 126, 254, 126, 255, 126, 255, 126, 254, 126, 255,
		126, 255, 255, 126, 254, 126, 255, 126, 255, 126, 255, 255, 126, 255, 255, 126, 254, 126, 254, 126, 254, 126, 255,
		255, 126, 255, 126, 255, 126, 255, 126, 255, 255, 126, 254, 126, 126, 255, 126, 255, 255, 255, 126, 255, 126, 255,
		255, 255, 126, 255, 255, 126, 255, 255, 126, 255, 126, 255, 255, 126, 255, 126, 255, 126, 255, 255, 126, 255,
	}
	muteAmrNB = []byte{243, 230, 110, 172, 190, 181, 254, 112, 112, 33, 249, 123, 249, 153, 17, 216, 176, 0, 8, 38, 178, 101, 243, 180, 0, 1, 39, 81, 248, 56, 145, 224}
	muteAmrWB = []byte{
		132, 84, 17, 136, 12, 239, 179, 173, 89, 64, 156, 0, 106, 87, 3, 167, 63, 179, 44, 17, 193, 1, 2, 232, 1, 49, 193,
		241, 72, 38, 4, 79, 13, 234, 20, 253, 156, 23, 148, 1, 148, 224, 31, 193, 164, 4, 108, 148, 8, 76, 57, 167, 92, 151, 194, 252, 250, 194, 139, 11, 44,
	}

	audio          = map[string][]byte{"audio/amr": muteAmrNB, "audio/amr-wb": muteAmrWB, "audio/pcma": mutePcma, "audio/pcmu": mutePcmu}
	audioTimestamp = map[string]uint32{"audio/amr": 160, "audio/amr-wb": 320, "audio/pcma": 160, "audio/pcmu": 160}

	bytesBuffer = sync.Pool{New: func() interface{} {
		return make([]byte, 1600)
	}}
)

func Listen(ctx *engine.Context, port int) (*net.UDPConn, error) {
	addr, err := net.ResolveUDPAddr("udp", fmt.Sprintf("%s:%d", config.ImsHost, port))
	if err != nil {
		return nil, err
	}
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return nil, err
	}
	ctx.Async(func() {
		buf := bytesBuffer.Get().([]byte)
		defer func() {
			if v := recover(); v != nil {
				log.WarnK(ctx.GetString(engine.ContextSessionId), "Recovered from listen with %v", v)
			}
			bytesBuffer.Put(buf)
		}()
		for {
			select {
			case <-ctx.Done():
				log.InfoK(ctx.GetString(engine.ContextSessionId), "Closing connection")
				_ = conn.Close()
				return
			default:
				_ = conn.SetReadDeadline(time.Now().Add(10 * time.Millisecond))
				_, _, _ = conn.ReadFromUDP(buf)
			}
		}
	})
	return conn, nil
}

func AudioTransfer(ctx *engine.Context, conn *net.UDPConn, remote *net.UDPAddr, trackId string, trigger func(string, int) int) {
	track := rtp.NewTrack(trackId, config.audioMimeType[0], conn)
	err := track.Bind(rtp.TrackContext{Id: trackId, PT: audioPayloads[config.audioMimeType[0]], Addr: remote})
	if err != nil {
		log.WarnK(ctx.GetString(engine.ContextSessionId), "Bind %s to %s  failed: %v", conn.LocalAddr().String(), remote.String(), err)
		return
	}
	ticker := time.NewTicker(20 * time.Millisecond)
	defer ticker.Stop()

	v, _ := ctx.Get(trackId)
	ch, _ := v.(chan string)

	packet := rtp.NewPacket()
	timestamp := rtp.NewTimestamp()
	timeInterval := audioTimestamp[config.audioMimeType[0]]
	packet.SetPayload(audio[config.audioMimeType[0]])
loop:
	for {
		select {
		case <-ctx.Done():
			log.InfoK(ctx.GetString(engine.ContextSessionId), "Closing audio transfer")
			return
		case e := <-ch:
			switch e {
			case KeySessionPickup:
				break loop
			}
		case <-ticker.C:
			packet.SetTimestamp(timestamp)
			err = track.WriteP(ctx, packet)
			if err != nil {
				log.WarnK(ctx.GetString(engine.ContextSessionId), "Write %s to %s failed: %v", conn.LocalAddr().String(), remote.String(), err)
			}
			timestamp += timeInterval
		}
	}
	// The call voice simulation
	defer func() {
		v, _ = ctx.Get(KeyEnd)
		v.(chan string) <- KeyEnd
	}()
	version := 1
	for i := 0; i < len(config.audioMimeType); i++ {
		version = trigger(config.audioMimeType[i], version)
		timeInterval = audioTimestamp[config.audioMimeType[i]]
		_ = track.Rebind(rtp.TrackContext{Id: trackId, PT: audioPayloads[config.audioMimeType[i]]})
		data := config.callerAudioData[i]
		if trackId == TrackCallee {
			data = config.calleeAudioData[i]
		}
		for j := 0; j < len(data); j++ {
			select {
			case <-ctx.Done():
				log.InfoK(ctx.GetString(engine.ContextSessionId), "Closing audio transfer")
				return
			case <-ticker.C:
				packet.SetPayload(data[j])
				packet.SetTimestamp(timestamp)
				err = track.WriteP(ctx, packet)
				if err != nil {
					log.WarnK(ctx.GetString(engine.ContextSessionId), "Write %s to %s failed: %v", conn.LocalAddr().String(), remote.String(), err)
				}
				timestamp += timeInterval
			}
		}
	}
	log.InfoK(ctx.GetString(engine.ContextSessionId), "End audio transfer")
}