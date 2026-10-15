//go:build !windows

package cmd

import (
	"bytes"
	"errors"
	"os/exec"
	"strings"
)

func Exec(args ...string) (string, error) {
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	command := exec.Command("bash", "-c", strings.Join(args, " "))
	command.Stdout = &stdout
	command.Stderr = &stderr
	err := command.Run()
	if err != nil {
		return "", err
	}
	if stderr.String() != "" {
		return "", errors.New(stderr.String())
	}
	return stdout.String(), nil
}