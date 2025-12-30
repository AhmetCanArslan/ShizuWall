#!/system/bin/sh

# daemon.sh - LADB ile wireless debugging açıkken çalıştır

DAEMON_PATH="/data/local/tmp/adb_daemon"
DEX_PATH="/data/local/tmp/daemon.dex"
PID_FILE="/data/local/tmp/daemon.pid"

echo "--- Daemon Startup Log ---"
echo "Date: $(date)"
echo "DEX Path: $DEX_PATH"

# Kill existing daemon if running
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if [ -d /proc/$OLD_PID ]; then
        echo "Killing old daemon (PID $OLD_PID)..."
        kill -9 $OLD_PID
    fi
    rm "$PID_FILE"
fi

# Check if DEX exists and has size
if [ ! -s "$DEX_PATH" ]; then
    echo "ERROR: $DEX_PATH is missing or empty!"
    ls -l $DEX_PATH
    exit 1
fi

# Binary'yi kopyala ve çalıştır
# if [ -f "$DAEMON_PATH" ]; then
#     echo "Starting native binary..."
#     chmod 755 "$DAEMON_PATH"
#     nohup "$DAEMON_PATH" > /dev/null 2>&1 &
# fi

# app_process ile başlat (system context)
echo "Starting app_process..."
LOG_FILE="/data/local/tmp/daemon.log"
nohup env CLASSPATH=$DEX_PATH /system/bin/app_process /system/bin \
    com.arslan.shizuwall.daemon.SystemDaemon > $LOG_FILE 2>&1 &

PID=$!
echo $PID > $PID_FILE
echo "App process started with PID $PID. Logs at $LOG_FILE"

# Quick check
sleep 1
if [ -d /proc/$PID ]; then
    echo "Daemon process is alive."
else
    echo "Daemon process died immediately."
    exit 1
fi

exit 0

exit 0
