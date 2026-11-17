#!/usr/bin/env bash
# GreyDrive server launcher (Linux).
# GC: serial = lowest memory footprint (default); zgc = generational ZGC, lower pause but more RSS.
# Use GREYDRIVE_GC=zgc to enable ZGC, or override JAVA_OPTS entirely.
set -uo pipefail

cd "$(dirname "$0")" || exit 1

JAR="GreyDrive.jar"
if [ ! -f "$JAR" ]; then
    echo "[ERROR] $JAR not found. Put it in the same folder as this script."
    exit 1
fi

JAVA_EXE="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [ ! -x "${JAVA_EXE:-}" ]; then
    JAVA_EXE="$(command -v java || true)"
fi
if [ -z "$JAVA_EXE" ]; then
    echo "[ERROR] Java not found. Install JDK 21+ and set JAVA_HOME or PATH."
    echo "        https://adoptium.net/temurin/releases/?version=21"
    exit 1
fi

JVER="$("$JAVA_EXE" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
JMAJOR="${JVER%%.*}"
if [ "$JMAJOR" = "1" ]; then
    JMAJOR="$(printf '%s' "$JVER" | cut -d. -f2)"
fi
JMAJOR="${JMAJOR//[!0-9]/}"
[ -n "$JMAJOR" ] || JMAJOR=0
if [ "$JMAJOR" -lt 21 ]; then
    echo "[ERROR] JDK 21 or newer is required. Current: $JVER"
    exit 1
fi

# Keep data persistent even when launched from an unpacked (temp) directory.
DATA_HOME="${GREYDRIVE_DATA_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/GreyDrive/data}"
if [ ! -e "data" ]; then
    mkdir -p "$DATA_HOME" 2>/dev/null
    ln -s "$DATA_HOME" data 2>/dev/null || {
        mkdir -p data
        echo "[WARN] Could not create the persistent data link; data stays in $PWD/data"
    }
fi

LOGDIR="$PWD/data/logs"
mkdir -p "$LOGDIR" 2>/dev/null
LOG="$LOGDIR/greydrive.log"
CONSOLE_LOG="$LOGDIR/console.log"
[ -f "$LOG" ] || : > "$LOG"
[ -f "$CONSOLE_LOG" ] || : > "$CONSOLE_LOG"

if [ -z "${JAVA_OPTS:-}" ]; then
    GC_MODE="${GREYDRIVE_GC:-serial}"
    GC_OPTS="-XX:+UseSerialGC"
    if [ "$GC_MODE" = "zgc" ]; then
        GC_OPTS="-XX:+UseZGC"
        if [ "$JMAJOR" -lt 23 ]; then
            GC_OPTS="$GC_OPTS -XX:+ZGenerational"
        fi
    fi
    JAVA_OPTS="-Xms64m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=128m $GC_OPTS"
fi

cat <<'BANNER'

   ______                          ______            _
  /  ___/  ______   __  __  ____  / / _ \   _  __   (_)   _____    ___
 /   \  \  \_  __ \ / / / /  / _/ / /  __/ | |/ /  / /   / / _ \  / _ \
 \    \  \  |  | \/ / /_/ / / /_/ / /_/ /  | |/ /  / /   / /  __/ /  __/
  \______/  |__|    \__, / /____/  \____/  |___/_ /_/    \___/  \___/
                   /____/

 :: GreyDrive Server :: by GreyZ

BANNER

echo "=========================================="
echo "  GreyDrive starting...  Press Ctrl+C to stop"
echo "  Java : $JVER"
echo "  Data : $PWD/data"
echo "  Log  : $LOG"
echo "  Logs : tail -f \"$LOG\""
echo "=========================================="
echo

WATCHER=""
cleanup() {
    if [ -n "$WATCHER" ]; then
        kill "$WATCHER" 2>/dev/null
        wait "$WATCHER" 2>/dev/null
    fi
}
trap cleanup EXIT INT TERM

# shellcheck disable=SC2086
"$JAVA_EXE" $JAVA_OPTS -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    -jar "$JAR" "$@" >> "$CONSOLE_LOG" 2>&1 &
JAVA_PID=$!

# Print the access URLs and Done!(xx.xxs) without letting Spring logs cover the banner.
(
    while IFS= read -r line; do
        case "$line" in
            *"Started GreyDriveApplication in "*)
                sec="$(printf '%s\n' "$line" | sed -n 's/.*Started GreyDriveApplication in \([0-9][0-9.]*\) seconds.*/\1/p')"
                sleep 1
                tail -n 40 "$LOG" | grep -E '服务/API|管理后台|Swagger:|OpenAPI:' | tail -n 4 || true
                if [ -n "$sec" ]; then
                    printf 'Done!(%.2fs)\n' "$sec"
                else
                    echo "Done!"
                fi
                break
                ;;
        esac
    done < <(tail -n 0 -F --pid="$JAVA_PID" "$LOG" 2>/dev/null)
) &
WATCHER=$!

wait "$JAVA_PID"
EXITCODE=$?

echo
if [ "$EXITCODE" -ne 0 ]; then
    echo "[ERROR] GreyDrive exited with code $EXITCODE. See the log: $LOG"
else
    echo "GreyDrive stopped."
fi
exit "$EXITCODE"
