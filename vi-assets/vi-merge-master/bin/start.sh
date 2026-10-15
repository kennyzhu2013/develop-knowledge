#!/bin/bash

file="./ffmpeg"
if [ -f "$file" ]; then
    chmod 755 "$file"
    echo "Set permission 755 on $file"
else
    echo "$file not found"
fi

GOTRACEBACK=crash nohup ./vi-merge >/dev/null 2>&1 &