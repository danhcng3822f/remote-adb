#!/usr/bin/env bash
# Connect to Remote ADB device via Relay Server (Linux / macOS)

set -e

KEY_PATH="$HOME/.android/adbkey.pub"

if [ "$1" == "--show-key" ] || [ "$1" == "-k" ]; then
    if [ -f "$KEY_PATH" ]; then
        echo -e "\033[0;36m================ ADB PUBLIC KEY ================\033[0m"
        cat "$KEY_PATH"
        echo ""
        echo -e "\033[0;36m================================================\033[0m"
        echo "Copy the key above or add it to Remote ADB app on your phone."
    else
        echo "Error: Could not find adbkey.pub at $KEY_PATH." >&2
        exit 1
    fi
    exit 0
fi

if [ -z "$1" ] || [ -z "$2" ]; then
    echo "Usage: ./adb-remote.sh <SERVER_HOST> <PORT> [--scrcpy]"
    echo "   Or: ./adb-remote.sh --show-key"
    exit 1
fi

SERVER=$1
PORT=$2
SCRCPY=$3

TARGET="$SERVER:$PORT"
echo -e "\033[0;36mConnecting to remote ADB at $TARGET...\033[0m"

adb connect "$TARGET"

if [ "$SCRCPY" == "--scrcpy" ]; then
    echo -e "\033[0;32mLaunching scrcpy...\033[0m"
    scrcpy -s "$TARGET"
fi
