#!/bin/bash

# 4School ADB Reverse Port Forwarding Script
# This script maps a port on your Android device to your PC's localhost.

PORT=${1:-8080}
DEVICE=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo -e "\033[0;31mError: No ADB device found.\033[0m"
    exit 1
fi

echo -e "\033[0;32m==> Reversing port $PORT on device $DEVICE...\033[0m"
adb -s $DEVICE reverse tcp:$PORT tcp:$PORT

if [ $? -eq 0 ]; then
    echo -e "\033[0;32mSuccess! Your Android app can now access PC localhost:$PORT via http://localhost:$PORT\033[0m"
else
    echo -e "\033[0;31mError: Failed to set up port reversal.\033[0m"
    exit 1
fi
