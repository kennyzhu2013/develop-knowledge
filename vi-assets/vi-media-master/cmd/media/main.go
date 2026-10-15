package main

import (
	"cmic.chinamobile.com/ziyan/go-sdk/version"
	"github.com/spf13/cobra"
	"vi-media/internal"
)

func main() {
	rootCmd := &cobra.Command{
		RunE: func(cmd *cobra.Command, args []string) error {
			cfg, _ := cmd.Flags().GetString("conf")
			app, err := internal.New(cfg)
			if err != nil {
				return err
			}
			err = app.ServeAndListen()
			_ = app.Shutdown()
			return err
		},
	}
	rootCmd.AddCommand(version.Command())
	rootCmd.Flags().String("conf", "", "the conf file location for the program")
	if err := rootCmd.Execute(); err != nil {
		panic(err)
	}
}