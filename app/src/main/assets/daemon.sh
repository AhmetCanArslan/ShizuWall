#!/system/bin/sh

# daemon.sh - ShizuWall persistent daemon launcher
# Run via LADB with wireless debugging enabled

DEX_PATH="/data/local/tmp/daemon.dex"
PID_FILE="/data/local/tmp/daemon.pid"
LOG_FILE="/data/local/tmp/daemon.log"
TOKEN_FILE="/data/local/tmp/shizuwall.token"

echo "=== ShizuWall Daemon Startup ==="
echo "Date: $(date)"
echo "DEX Path: $DEX_PATH"

# Verify required files
if [ ! -s "$DEX_PATH" ]; then
    echo "ERROR: $DEX_PATH is missing or empty!"
    ls -la "$DEX_PATH" 2>/dev/null
    exit 1
fi

if [ ! -f "$TOKEN_FILE" ]; then
    echo "ERROR: Token file missing at $TOKEN_FILE"
    exit 1
fi

# Kill existing daemon if running
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if [ -d "/proc/$OLD_PID" ]; then
        echo "Stopping old daemon (PID $OLD_PID)..."
        kill -TERM "$OLD_PID" 2>/dev/null
        sleep 1
        # Force kill if still running
        if [ -d "/proc/$OLD_PID" ]; then
            kill -9 "$OLD_PID" 2>/dev/null
        fi
    fi
    rm -f "$PID_FILE"
fi

# Also kill by process name
pkill -f 'com.arslan.shizuwall.daemon.SystemDaemon' 2>/dev/null || true
sleep 1

# Clear old logs
: > "$LOG_FILE"

# Start daemon via app_process (runs with shell UID privileges)
echo "Starting app_process..."
nohup env CLASSPATH="$DEX_PATH" /system/bin/app_process /system/bin \
    com.arslan.shizuwall.daemon.SystemDaemon >> "$LOG_FILE" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"
echo "Daemon started with PID $PID"
echo "Logs: $LOG_FILE"

# Wait for startup
sleep 2

# Verify process is alive
if [ -d "/proc/$PID" ]; then
    echo "SUCCESS: Daemon process is running"
    # Show first few log lines
    echo "=== Initial log output ==="
    head -10 "$LOG_FILE"
    exit 0
else
    echo "FAILED: Daemon process died"
    echo "=== Error log ==="
    cat "$LOG_FILE"
    exit 1
fi
