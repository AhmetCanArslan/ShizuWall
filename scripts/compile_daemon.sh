#!/bin/bash

set -euo pipefail

# Configuration
DEFAULT_SDK_PATH="/Users/macjuan/Library/Android/sdk"
SDK_PATH="${ANDROID_SDK_ROOT:-$DEFAULT_SDK_PATH}"
LOCAL_PROPERTIES="local.properties"

if [ -f "$LOCAL_PROPERTIES" ]; then
    PROP_SDK_PATH=$(grep '^sdk.dir=' "$LOCAL_PROPERTIES" | head -n1 | cut -d'=' -f2- | sed 's|\\:|:|g')
    if [ -n "${PROP_SDK_PATH:-}" ]; then
        SDK_PATH="$PROP_SDK_PATH"
    fi
fi

if [ ! -d "$SDK_PATH" ]; then
    echo "SDK path not found: $SDK_PATH"
    exit 1
fi

BUILD_TOOLS_VER=${BUILD_TOOLS_VER:-$(ls -1 "$SDK_PATH/build-tools" | tr -d '/' | sort -V | tail -n1)}
PLATFORM_NAME=${PLATFORM_NAME:-$(ls -1 "$SDK_PATH/platforms" | tr -d '/' | sort -V | tail -n1)}

if [ -z "${BUILD_TOOLS_VER:-}" ] || [ -z "${PLATFORM_NAME:-}" ]; then
    echo "Could not detect build-tools/platform versions in SDK path: $SDK_PATH"
    exit 1
fi

ANDROID_JAR="$SDK_PATH/platforms/$PLATFORM_NAME/android.jar"
D8_BIN="$SDK_PATH/build-tools/$BUILD_TOOLS_VER/d8"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "android.jar not found: $ANDROID_JAR"
    exit 1
fi

if [ ! -x "$D8_BIN" ]; then
    echo "d8 not found or not executable: $D8_BIN"
    exit 1
fi

JAVA_FILE="app/src/main/java/com/arslan/shizuwall/daemon/SystemDaemon.java"
OUT_DIR="app/build/daemon_temp"
ASSETS_DIR="app/src/main/assets"

# Create temp directory
mkdir -p $OUT_DIR

echo "Compiling Java source..."
javac --release 11 -d $OUT_DIR \
    -classpath "$ANDROID_JAR" \
    $JAVA_FILE

echo "Converting to DEX..."
$D8_BIN \
    --output $OUT_DIR/daemon.zip \
    $OUT_DIR/com/arslan/shizuwall/daemon/*.class \
    --lib "$ANDROID_JAR"

# Extract classes.dex from the zip and rename to daemon.bin
unzip -p $OUT_DIR/daemon.zip classes.dex > $ASSETS_DIR/daemon.bin

echo "Success! daemon.bin created in $ASSETS_DIR"

# Cleanup
rm -rf $OUT_DIR
