#!/bin/bash

if [ -z "$1" ]
then
  echo "Usage: $0 <version>"
  exit 1
fi

rm -f bin/offline-merge

path="offline-merge/pkg/version"
version=${path}.Version=$1
gitCommit=${path}.GitCommit=unknown
buildTime=${path}.BuildTime=$(date +"%Y-%m-%d_%H:%m:%S")

go build -ldflags "-X ${version} -X ${gitCommit} -X ${buildTime}" -o bin/offline-merge cmd/offlinemerge/main.go