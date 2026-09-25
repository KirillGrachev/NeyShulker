#!/usr/bin/env bash
# Смоук-тест на реальном сервере: Paper стартует с плагином, плагин
# включается без ошибок и корректно переживает остановку сервера.
#
# Использование: ci/smoke-test.sh <paper-version> <plugin-jar>
# Переменные: JAVA (по умолчанию java из PATH), SMOKE_TIMEOUT (сек, по умолчанию 300).
set -euo pipefail

PAPER_VERSION="${1:?Usage: smoke-test.sh <paper-version> <plugin-jar>}"
PLUGIN_JAR="${2:?Usage: smoke-test.sh <paper-version> <plugin-jar>}"
JAVA_BIN="${JAVA:-java}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-300}"

WORKDIR="$(mktemp -d)"
LOG="$WORKDIR/server.log"
PID_FILE="$WORKDIR/server.pid"

cleanup() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        kill "$(cat "$PID_FILE")" 2>/dev/null || true
        sleep 5
        kill -9 "$(cat "$PID_FILE")" 2>/dev/null || true
    fi
    rm -rf "$WORKDIR"
}
trap cleanup EXIT

echo "[smoke] java: $("$JAVA_BIN" -version 2>&1 | head -1)"

echo "[smoke] resolving Paper $PAPER_VERSION build via fill.papermc.io"
DOWNLOAD_URL="$(python3 - "$PAPER_VERSION" <<'PY'
import json, sys, urllib.request

version = sys.argv[1]

def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'neyshulker-ci'})
    return json.load(urllib.request.urlopen(req, timeout=30))

data = get(f"https://fill.papermc.io/v3/projects/paper/versions/{version}/builds")
builds = data["builds"] if isinstance(data, dict) else data
if not builds:
    raise SystemExit(f"no builds for {version}")
print(builds[-1]["downloads"]["server:default"]["url"])
PY
)"

echo "[smoke] downloading $DOWNLOAD_URL"
python3 - "$DOWNLOAD_URL" "$WORKDIR/paper.jar" <<'PY'
import shutil, sys, urllib.request
url, dest = sys.argv[1], sys.argv[2]
req = urllib.request.Request(url, headers={'User-Agent': 'neyshulker-ci'})
with urllib.request.urlopen(req, timeout=300) as r, open(dest, 'wb') as f:
    shutil.copyfileobj(r, f)
PY

mkdir -p "$WORKDIR/plugins"
cp "$PLUGIN_JAR" "$WORKDIR/plugins/"
echo "eula=true" > "$WORKDIR/eula.txt"
cat > "$WORKDIR/server.properties" <<'PROPS'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-npcs=false
spawn-animals=false
spawn-monsters=false
max-players=1
sync-chunk-writes=false
view-distance=4
simulation-distance=4
PROPS

echo "[smoke] starting server"
( cd "$WORKDIR" && exec "$JAVA_BIN" -Xms256M -Xmx"${SMOKE_XMX:-1024M}" -jar paper.jar --nogui ) > "$LOG" 2>&1 &
echo $! > "$PID_FILE"
SERVER_PID="$(cat "$PID_FILE")"

STATUS=0

# Ждем полной загрузки
if ! timeout "$SMOKE_TIMEOUT" bash -c '
    LOG="'"$LOG"'"; PID="'"$SERVER_PID"'"
    until grep -qE "Done \(.*\)! For help" "$LOG" 2>/dev/null; do
        kill -0 "$PID" 2>/dev/null || { echo "[smoke] server died during startup"; exit 1; }
        sleep 2
    done'; then
    echo "[smoke] FAIL: server did not finish startup in ${SMOKE_TIMEOUT}s"
    STATUS=1
fi

if [ "$STATUS" -eq 0 ]; then
    echo "[smoke] server is up, checking plugin state"

    if grep -q "NeyShulker started successfully" "$LOG"; then
        echo "[smoke] OK: plugin enabled"
    else
        echo "[smoke] FAIL: plugin enable message not found"
        STATUS=1
    fi

    if grep -qE "Error occurred while enabling NeyShulker|Could not load .*NeyShulker|IncompatibleClassChangeError|NoSuchMethodError" "$LOG"; then
        echo "[smoke] FAIL: plugin load/enable error in log"
        STATUS=1
    fi
fi

echo "[smoke] stopping server"
kill "$SERVER_PID" 2>/dev/null || true
timeout 120 bash -c 'while kill -0 "'"$SERVER_PID"'" 2>/dev/null; do sleep 2; done' \
    || kill -9 "$SERVER_PID" 2>/dev/null || true

if grep -q "NeyShulker stopped" "$LOG"; then
    echo "[smoke] OK: graceful disable"
else
    echo "[smoke] WARN: graceful disable message not found"
fi

if [ "$STATUS" -ne 0 ]; then
    echo "----- server.log (tail) -----"
    tail -n 120 "$LOG"
    exit "$STATUS"
fi

echo "[smoke] PASS"
