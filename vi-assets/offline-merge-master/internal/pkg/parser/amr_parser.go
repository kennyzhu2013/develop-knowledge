// AmrParser is a Go port of AmrParser.java.
//
// It parses AMR-NB / AMR-WB files in the "file storage format" (magic header
// plus a stream of self-describing frames) and reports frame count, duration,
// SID (silence descriptor) count, mode histogram and average bitrate.
//
// AMR file storage frame header layout (1 byte):
//
//	bit7 P | bit6..3 FT | bit2 Q | bit1..0 P
//
// Frame type (FT) tables:
//
//	AMR-NB:  FT 0~7  speech        | frame sizes below
//	         FT 8~11 comfort noise | AMR SID / GSM-EFR SID / TDMA-EFR SID / PDC-EFR SID, 6 B
//	         FT 12~14 reserved     | no defined frame length -> parsing stops
//	         FT=15   NO_DATA       | 1 B
//	AMR-WB:  FT 0~8  speech        | frame sizes below
//	         FT=9    SID           | 6 B
//	         FT 10~13 reserved     | no defined frame length -> parsing stops
//	         FT=14   SPEECH_LOST   | 1 B
//	         FT=15   NO_DATA       | 1 B
//
// References:
//   - AMR-NB: 3GPP TS 26.101 Table 1a / RFC 3267 Table 1
//   - AMR-WB: 3GPP TS 26.201 Table 1a
//
// Speech, comfort-noise (SID) and SPEECH_LOST frames each occupy one 20 ms time
// slot. A NO_DATA frame carries no speech content and is therefore excluded
// from both the frame count and the duration.
package parser

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"sort"
	"time"
)

// AmrType identifies the AMR codec variant.
type AmrType int

const (
	AmrUnknown AmrType = iota
	AmrNB
	AmrWB
)

func (t AmrType) String() string {
	switch t {
	case AmrNB:
		return "AMR_NB"
	case AmrWB:
		return "AMR_WB"
	default:
		return "UNKNOWN"
	}
}

// AMR-NB frame sizes for FT 0~7, including the 1-byte frame header.
var amrNBFrameSize = [8]int{13, 14, 16, 18, 20, 21, 27, 32}

// AMR-WB frame sizes for FT 0~8, including the 1-byte frame header.
var amrWBFrameSize = [9]int{18, 24, 33, 37, 41, 47, 51, 59, 61}

// Magic numbers of the AMR file storage format.
var (
	amrNBHeader = []byte("#!AMR\n")
	amrWBHeader = []byte("#!AMR-WB\n")
)

const (
	// FT 8~11 AMR-NB comfort-noise frames (AMR SID / GSM-EFR SID / TDMA-EFR SID /
	// PDC-EFR SID): 1-byte frame header + 5-byte payload (39 bits).
	amrNBSIDFrameLen = 6
	// FT=9 AMR-WB SID: 1-byte frame header + 5-byte payload (35 bits).
	amrWBSIDFrameLen = 6

	// noPayloadFrameLen is the length of every frame that carries nothing beyond
	// its header: FT=15 NO_DATA (both codecs) and FT=14 SPEECH_LOST (AMR-WB).
	noPayloadFrameLen = 1

	// Every speech / comfort-noise / SPEECH_LOST frame carries 20 ms of audio.
	frameDurationSec = 0.02
)

// AmrStat holds the aggregated result of a parse run.
type AmrStat struct {
	Type        AmrType
	TotalFrames int
	// SIDFrameCount counts comfort-noise frames: AMR-NB FT 8~11 and AMR-WB FT=9.
	SIDFrameCount int
	// SpeechLostFrameCount counts AMR-WB FT=14 SPEECH_LOST frames.
	// They are reconstructed by error concealment, so they occupy a 20 ms slot.
	SpeechLostFrameCount int
	TotalSeconds         float64

	// ModeCount is the speech-frame histogram: frame type -> number of frames.
	ModeCount map[int]int

	// AvgBitrateKbps is the net audio bitrate (file header excluded), in kbps.
	AvgBitrateKbps float64
	// TotalBytes is every frame byte consumed, file header excluded.
	TotalBytes int64

	// Stopped reports whether parsing aborted early on a reserved/invalid frame type.
	Stopped bool
	// Truncated reports whether trailing bytes shorter than one frame were dropped.
	Truncated bool
	// StopPos is the byte offset at which parsing ended or aborted.
	StopPos int
}

// IsComplete reports whether the result covers the whole input.
func (s *AmrStat) IsComplete() bool { return !s.Stopped && !s.Truncated }

// Parse errors.
var (
	ErrEmptyData     = errors.New("empty data: input byte slice is nil or zero-length")
	ErrUnknownHeader = errors.New(`unrecognized AMR header: expected "#!AMR\n" or "#!AMR-WB\n"; ` +
		"for raw streams call ParseRaw(data, AmrType) instead")
)

// ParseFile reads path and parses it as a headered AMR file.
func ParseFile(path string) (*AmrStat, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return Parse(data)
}

// Parse auto-detects AMR-NB / AMR-WB from the magic header and parses data.
//
// For headerless (raw) streams use ParseRaw instead.
func Parse(data []byte) (*AmrStat, error) {
	if len(data) == 0 {
		return nil, ErrEmptyData
	}

	var (
		typ        AmrType
		frameTable []int
		pos        int
	)

	switch {
	case bytes.HasPrefix(data, amrNBHeader):
		typ = AmrNB
		frameTable = amrNBFrameSize[:]
		pos = len(amrNBHeader)
	case bytes.HasPrefix(data, amrWBHeader):
		typ = AmrWB
		frameTable = amrWBFrameSize[:]
		pos = len(amrWBHeader)
	default:
		return nil, ErrUnknownHeader
	}

	return parseCore(data, pos, typ, frameTable), nil
}

// ParseRaw parses a headerless raw stream; typ must be AmrNB or AmrWB.
func ParseRaw(data []byte, typ AmrType) (*AmrStat, error) {
	if len(data) == 0 {
		return nil, ErrEmptyData
	}

	var frameTable []int
	switch typ {
	case AmrNB:
		frameTable = amrNBFrameSize[:]
	case AmrWB:
		frameTable = amrWBFrameSize[:]
	default:
		return nil, fmt.Errorf("invalid AMR type: %v; must be AmrNB or AmrWB", typ)
	}

	return parseCore(data, 0, typ, frameTable), nil
}

// parseCore walks the frame stream and accumulates statistics.
func parseCore(data []byte, pos int, typ AmrType, frameTable []int) *AmrStat {
	stat := &AmrStat{
		Type:      typ,
		ModeCount: make(map[int]int),
	}
	maxVoiceFt := len(frameTable) - 1

	var totalBytes int64

	for pos < len(data) {
		ft := (int(data[pos]) & 0x78) >> 3 // Frame Type
		var (
			frameLen         int
			occupiesTimeSlot bool
		)

		if typ == AmrNB {
			switch {
			case ft <= maxVoiceFt: // FT 0~7: speech
				frameLen = frameTable[ft]
				stat.ModeCount[ft]++
				occupiesTimeSlot = true
			case ft >= 8 && ft <= 11: // FT 8~11: comfort noise, 1-byte header + 5-byte payload
				frameLen = amrNBSIDFrameLen
				stat.SIDFrameCount++
				occupiesTimeSlot = true
			case ft == 15: // FT=15: NO_DATA, header only, no time slot
				frameLen = noPayloadFrameLen
				occupiesTimeSlot = false
			default: // FT 12~14: reserved, no defined frame length -> stop
				stat.Stopped = true
				return finish(stat, pos, totalBytes)
			}
		} else {
			switch {
			case ft <= maxVoiceFt: // FT 0~8: speech
				frameLen = frameTable[ft]
				stat.ModeCount[ft]++
				occupiesTimeSlot = true
			case ft == 9: // FT=9: AMR-WB SID, 1-byte header + 5-byte payload
				frameLen = amrWBSIDFrameLen
				stat.SIDFrameCount++
				occupiesTimeSlot = true
			case ft == 14: // FT=14: SPEECH_LOST, header only, but still a 20 ms slot
				frameLen = noPayloadFrameLen
				stat.SpeechLostFrameCount++
				occupiesTimeSlot = true
			case ft == 15: // FT=15: NO_DATA, header only, no time slot
				frameLen = noPayloadFrameLen
				occupiesTimeSlot = false
			default: // FT 10~13: reserved, no defined frame length -> stop
				stat.Stopped = true
				return finish(stat, pos, totalBytes)
			}
		}

		if pos+frameLen > len(data) {
			stat.Truncated = true // trailing bytes shorter than one frame
			return finish(stat, pos, totalBytes)
		}

		pos += frameLen
		totalBytes += int64(frameLen)
		if occupiesTimeSlot {
			stat.TotalFrames++
		}
	}

	return finish(stat, pos, totalBytes)
}

// finish derives duration and bitrate; it centralises the tail of parseCore so
// the early-exit branches stay consistent.
func finish(stat *AmrStat, stopPos int, totalBytes int64) *AmrStat {
	stat.StopPos = stopPos
	stat.TotalBytes = totalBytes
	stat.TotalSeconds = float64(stat.TotalFrames) * frameDurationSec
	if stat.TotalSeconds > 0 {
		stat.AvgBitrateKbps = float64(totalBytes) * 8.0 / stat.TotalSeconds / 1000.0
	}
	return stat
}

func main() {
	paths := []string{
		// Duration: 00:01:21.78, bitrate: 24 kb/s
		"Callee_70000000000000305706748688212217_20260915160332.amr",
		// 17.52
		"Caller_70000000000000305706887200907284_20260915160515.amr",
	}
	if len(os.Args) > 1 {
		paths = os.Args[1:]
	}

	for i, path := range paths {
		if i > 0 {
			fmt.Println("============================")
		}
		printStat(path)
	}
}

func printStat(path string) {
	start := time.Now()

	stat, err := ParseFile(path)
	if err != nil {
		fmt.Printf("failed to parse %s: %v\n", path, err)
		return
	}

	fmt.Printf("file: %s\n", path)
	fmt.Printf("AMR type: %v\n", stat.Type)
	fmt.Printf("total frames (speech+SID+SPEECH_LOST): %d\n", stat.TotalFrames)
	fmt.Printf("SID frames: %d\n", stat.SIDFrameCount)
	if stat.SpeechLostFrameCount > 0 {
		fmt.Printf("SPEECH_LOST frames: %d\n", stat.SpeechLostFrameCount)
	}
	fmt.Printf("duration: %.3f s\n", stat.TotalSeconds)
	fmt.Printf("avg bitrate: %.2f kbps\n", stat.AvgBitrateKbps)

	fmt.Printf("complete: %v", stat.IsComplete())
	if stat.Stopped {
		fmt.Printf(" (stopped at byte %d: reserved/invalid frame type)", stat.StopPos)
	}
	if stat.Truncated {
		fmt.Printf(" (trailing bytes shorter than one frame)")
	}
	fmt.Println()

	fmt.Println("speech frame mode histogram:")
	modes := make([]int, 0, len(stat.ModeCount))
	for m := range stat.ModeCount {
		modes = append(modes, m)
	}
	sort.Ints(modes)
	for _, m := range modes {
		fmt.Printf(" mode%d: %d frames\n", m, stat.ModeCount[m])
	}

	fmt.Printf("elapsed: %d ms\n", time.Since(start).Milliseconds())
}
