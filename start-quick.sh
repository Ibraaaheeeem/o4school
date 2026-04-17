#!/bin/bash
# Quick startup script - assumes build is already done
export $(grep -v '^#' /home/abuhaneefayn/Desktop/4school/.env | grep -v '^$' | xargs)
cd /home/abuhaneefayn/Desktop/4school
./gradlew webapp:bootRun
