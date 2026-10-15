package internal

type Config struct {
	MaxG int
	MinD int
	Loop bool

	ImsPort    int
	ImsHost    string
	ImsServer  string
	LocalAddr  string
	ServerAddr string

	CallerAudioSource string
	CalleeAudioSource string
	audioMimeType     []string
	callerAudioData   [][][]byte
	calleeAudioData   [][][]byte

	StreamStartFrames int
	StreamSampleRate  int
	StreamSegments   int
}