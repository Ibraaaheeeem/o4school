#!/bin/bash
# Load environment variables from .env file (skip comments and empty lines)
export $(grep -v '^#' /home/abuhaneefayn/Desktop/4school/.env | grep -v '^$' | xargs)

# Navigate to project directory
cd /home/abuhaneefayn/Desktop/4school

# Stop any existing processes
./gradlew --stop 2>/dev/null || true

# Build and run
./gradlew clean build -x test && ./gradlew webapp:bootRun
