#!/usr/bin/env sh
set -eu
gradle --no-daemon assembleDebug
printf '\nAPK: app/build/outputs/apk/debug/app-debug.apk\n'
