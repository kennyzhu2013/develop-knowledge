package main

import (
	"github.com/spf13/cobra"
	"simulation/internal"
)

func main() {
	var cfg internal.Config
	rootCmd := &cobra.Command{
		Short: "Call Simulator",
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) != 2 {
				return cmd.UsageFunc()(cmd)
			}
			cfg.LocalAddr = args[0]
			cfg.ServerAddr = args[1]
			app, err := internal.New(cfg)
			if err != nil {
				return err
			}
			if err = app.Start(); err != nil {
				return err
			}
			return nil
		},
	}
	rootCmd.Flags().BoolVarP(&cfg.Loop, "loop", "c", false, "whether it is cyclic")
	rootCmd.Flags().IntVarP(&cfg.MaxG, "goroutines", "g", 1, "concurrency")
	rootCmd.Flags().IntVarP(&cfg.MinD, "duration", "d", 80, "minimum execution time, in seconds")
	rootCmd.Flags().IntVarP(&cfg.ImsPort, "imsPort", "p", 20000, "starting RTP Port")
	rootCmd.Flags().IntVarP(&cfg.StreamStartFrames, "streamStartFrames", "t", 5, "the stream start frames, unit seconds")
	rootCmd.Flags().IntVarP(&cfg.StreamSampleRate, "streamSampleRate", "r", 16000, "the stream sample rate")
	rootCmd.Flags().IntVarP(&cfg.StreamSegments, "streamSegments", "f", 5, "the stream segment count")
	rootCmd.Flags().StringVarP(&cfg.ImsHost, "imsHost", "i", "", "local IMS host")
	rootCmd.Flags().StringVarP(&cfg.ImsServer, "imsServer", "s", "", "server IMS host")
	rootCmd.Flags().StringVarP(&cfg.CallerAudioSource, "callerAudioSource", "a", "",
		"The media simulation audio file, with a file name organized in the format of encoding type @ file name, and multiple files separated by ‘,’. Encoding types include: amr, amr-wb, pcma, pcmu")
	rootCmd.Flags().StringVarP(&cfg.CalleeAudioSource, "calleeAudioSource", "b", "",
		"The media simulation audio file, with a file name organized in the format of encoding type @ file name, and multiple files separated by ‘,’. Encoding types include: amr, amr-wb, pcma, pcmu")
	if err := rootCmd.Execute(); err != nil {
		panic(err)
	}
}