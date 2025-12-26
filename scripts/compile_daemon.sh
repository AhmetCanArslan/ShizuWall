#!/bin/bash

# Configuration
SDK_PATH="/Users/macjuan/Library/Android/sdk"
BUILD_TOOLS_VER="35.0.0"
PLATFORM_VER="36"

JAVA_FILE="app/src/main/java/com/arslan/shizuwall/daemon/SystemDaemon.java"
OUT_DIR="app/build/daemon_temp"
ASSETS_DIR="app/src/main/assets"

# Create temp directory
mkdir -p $OUT_DIR

echo "Compiling Java source..."
javac --release 11 -d $OUT_DIR \
    -classpath "$SDK_PATH/platforms/android-$PLATFORM_VER/android.jar" \
    $JAVA_FILE

if [ $? -ne 0 ]; then
    echo "Java compilation failed!"
    exit 1
fi

echo "Converting to DEX..."
$SDK_PATH/build-tools/$BUILD_TOOLS_VER/d8 \
    --output $OUT_DIR/daemon.zip \
    $OUT_DIR/com/arslan/shizuwall/daemon/*.class \
    --lib "$SDK_PATH/platforms/android-$PLATFORM_VER/android.jar"

if [ $? -ne 0 ]; then
    echo "DEX conversion failed!"
    exit 1
fi

# Extract classes.dex from the zip and rename to daemon.bin
unzip -p $OUT_DIR/daemon.zip classes.dex > $ASSETS_DIR/daemon.bin

echo "Success! daemon.bin created in $ASSETS_DIR"

# Cleanup
rm -rf $OUT_DIR
