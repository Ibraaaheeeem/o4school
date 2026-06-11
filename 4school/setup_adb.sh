#!/bin/bash

# 4School ADB Setup Script
# This script automates building, installing, and debugging on any connected ADB device.

# Set JAVA_HOME
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

PACKAGE_NAME="com.haneef.school"
ACTIVITY_NAME=".MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}==> Checking ADB connectivity...${NC}"

# Find connected devices
DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | awk '{print $1}')
DEVICE_COUNT=$(echo "$DEVICES" | grep -v "^$" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}Error: No ADB device found.${NC}"
    echo "Please ensure a device is connected and visible in 'adb devices'."
    exit 1
elif [ "$DEVICE_COUNT" -gt 1 ]; then
    echo -e "${GREEN}Multiple devices found:${NC}"
    echo "$DEVICES"
    # Select the first one for now, or you could prompt the user
    DEVICE=$(echo "$DEVICES" | head -n 1)
    echo -e "${GREEN}Using first device: $DEVICE${NC}"
else
    DEVICE=$(echo "$DEVICES")
    echo -e "${GREEN}Selected device: $DEVICE${NC}"
fi

if [[ "$1" == "--check" ]]; then
    echo -e "${GREEN}ADB connectivity verified!${NC}"
    exit 0
fi

echo -e "${GREEN}==> Building debug APK...${NC}"
./gradlew assembleDebug --console=plain

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Build failed.${NC}"
    exit 1
fi

echo -e "${GREEN}==> Installing APK to $DEVICE...${NC}"
adb -s $DEVICE install -r $APK_PATH

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Installation failed.${NC}"
    exit 1
fi

echo -e "${GREEN}==> Launching $PACKAGE_NAME...${NC}"
adb -s $DEVICE shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME"

# Wait up to 5 seconds for the app to start and get a valid PID
PID=""
for i in {1..5}; do
    PID=$(adb -s $DEVICE shell pidof -s $PACKAGE_NAME | tr -d '\r' | tr -d '\n')
    if [ -n "$PID" ]; then
        break
    fi
    sleep 1
done

if [ -n "$PID" ]; then
    adb -s $DEVICE logcat --pid=$PID
else
    echo -e "${RED}Warning: Could not find PID for $PACKAGE_NAME. Streaming all logs...${NC}"
    adb -s $DEVICE logcat *:V | grep $PACKAGE_NAME
fi
