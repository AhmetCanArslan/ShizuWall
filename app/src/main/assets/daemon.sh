#!/system/bin/sh

DEX_PATH="/data/local/tmp/daemon.dex"
PID_FILE="/data/local/tmp/daemon.pid"
LOG_FILE="/data/local/tmp/daemon.log"
TOKEN_FILE="/data/local/tmp/shizuwall.token"

echo "=== ShizuWall Daemon Startup ==="
echo "Date: $(date)"
echo "DEX Path: $DEX_PATH"

if [ ! -s "$DEX_PATH" ]; then
    echo "ERROR: $DEX_PATH is missing or empty!"
    ls -la "$DEX_PATH" 2>/dev/null
    exit 1
fi

if [ ! -f "$TOKEN_FILE" ]; then
    echo "ERROR: Token file missing at $TOKEN_FILE"
    exit 1
fi

if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if [ -d "/proc/$OLD_PID" ]; then
        echo "Stopping old daemon (PID $OLD_PID)..."
        kill -TERM "$OLD_PID" 2>/dev/null
        sleep 1
        if [ -d "/proc/$OLD_PID" ]; then
            kill -9 "$OLD_PID" 2>/dev/null
        fi
    fi
    rm -f "$PID_FILE"
fi

pkill -f 'com.arslan.shizuwall.daemon.SystemDaemon' 2>/dev/null || true
sleep 1

: > "$LOG_FILE"

echo "Starting app_process..."
nohup env CLASSPATH="$DEX_PATH" /system/bin/app_process /system/bin \
    com.arslan.shizuwall.daemon.SystemDaemon >> "$LOG_FILE" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"
echo "Daemon started with PID $PID"
echo "Logs: $LOG_FILE"

sleep 2

if [ -d "/proc/$PID" ]; then
    echo "SUCCESS: Daemon process is running"
    echo "=== Initial log output ==="
    head -10 "$LOG_FILE"
    exit 0
else
    echo "FAILED: Daemon process died"
    echo "=== Error log ==="
    cat "$LOG_FILE"
    exit 1
fi
