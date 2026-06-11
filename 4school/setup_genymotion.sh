#!/bin/bash

# 4School Genymotion Setup Script
# This script automates building, installing, and debugging on Genymotion.

# Set JAVA_HOME if not already set or if project needs specific version
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

PACKAGE_NAME="com.haneef.school"
ACTIVITY_NAME=".MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}==> Checking Genymotion connectivity...${NC}"

# Find Genymotion device (typically 127.0.0.1 or 192.168.5x)
DEVICE=$(adb devices | grep -E "127.0.0.1|192.168.5" | head -n 1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    # Fallback to any connected device if only one is present
    DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    if [ "$DEVICE_COUNT" -eq 1 ]; then
        DEVICE=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
        echo -e "${GREEN}==> No Genymotion device found. Using connected device: $DEVICE${NC}"
    else
        echo -e "${RED}Error: No unique device found.${NC}"
        echo "Please ensure a device is connected and visible in 'adb devices'."
        adb devices
        exit 1
    fi
fi

echo "Selected device: $DEVICE"

if [[ "$1" == "--check" ]]; then
    echo -e "${GREEN}Genymotion connectivity verified!${NC}"
    exit 0
fi

echo -e "${GREEN}==> Building debug APK...${NC}"
./gradlew assembleDebug

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
