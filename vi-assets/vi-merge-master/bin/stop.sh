#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <port_number>"
  exit 1
fi

PORT=$1

PID=$(lsof -ti :"$PORT")

if [ -z "$PID" ]; then
  echo "No process found occupying the port $PORT"
  exit 1
fi

echo "Preparing to terminate the following processes（PID: ${PID[0]}）:"
ps -p "${PID[0]}" -o pid,comm,args

echo "Terminating process..."
kill -TERM "${PID[0]}" 2>/dev/null

while true; do
  sleep 1
  if ! kill -0 "${PID[0]}" 2>/dev/null; then
    echo "All processes have successfully exited."
    break
  fi
done