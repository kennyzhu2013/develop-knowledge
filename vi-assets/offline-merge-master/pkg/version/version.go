package version

import (
	"fmt"
	"github.com/spf13/cobra"
	"os"
	"runtime"
)

var (
	Version   = ""
	GitCommit = ""
	BuildTime = ""
)

func Command() *cobra.Command {
	return &cobra.Command{Use: "version", Long: "print the program version", Args: cobra.ExactArgs(0),
		Run: func(cmd *cobra.Command, args []string) {
			fmt.Print(String())
			os.Exit(0)
		},
	}
}

func String() string {
	return fmt.Sprintf("Version: \t%s\nGo version: \t%s\nGit commit: \t%s\nBuilt: \t%s\nOS/Arch: \t%s/%s\n", Version, runtime.Version(), GitCommit, BuildTime, runtime.GOOS, runtime.GOARCH)
}
