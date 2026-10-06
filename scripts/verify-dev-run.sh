#!/bin/bash
# Dev-run verifier: starts the loader client, waits until Minecraft has decided on a rendering
# backend, prints the mod's log lines and then CLOSES the game again (Minecraft does not exit on its
# own, so this keeps automated checks from leaving a window behind).
#
# Usage: scripts/verify-dev-run.sh [fabric|neoforge]
#
# Requires a Java 25 toolchain; the Gradle user home defaults to ./.gradle-home so the check also
# works when $HOME is not writable (use GRADLE_USER_HOME / GRADLE_RO_DEP_CACHE to override).
set -u
LOADER="${1:-fabric}"
cd "$(dirname "$0")/.." || exit 1
G=$(ls -d "${GRADLE_HOME_DIST:-$HOME/.gradle}"/wrapper/dists/gradle-9.7.1-bin/*/gradle-9.7.1/bin/gradle 2>/dev/null | head -1)
if [ -z "$G" ]; then
  echo "Gradle 9.7.1 distribution not found; run ./gradlew once or set GRADLE_HOME_DIST" >&2
  exit 1
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"
export GRADLE_RO_DEP_CACHE="${GRADLE_RO_DEP_CACHE:-$HOME/.gradle/caches}"

LOGDIR=".research/run-$LOADER.log"
rm -f "$LOGDIR"
"$G" ":$LOADER:runClient" --no-daemon --console=plain > "$LOGDIR" 2>&1 &
GPID=$!

for _ in $(seq 1 90); do
  sleep 5
  if grep -q 'Using graphics backend' "$LOADER/run/logs/latest.log" 2>/dev/null; then
    sleep 8   # let the title screen come up
    break
  fi
done

pkill -f '[d]evlaunchinjector' 2>/dev/null
sleep 4
kill "$GPID" 2>/dev/null
wait "$GPID" 2>/dev/null

echo "=== $LOADER run: mod lines ==="
grep -E 'vkselect|Mixing VulkanBackendMixin' "$LOADER/run/logs/latest.log" 2>/dev/null | head -12
echo "=== backend ==="
grep -m2 'Using graphics backend' "$LOADER/run/logs/latest.log" 2>/dev/null
echo "=== problems ==="
grep -iE 'vkselect.*(error|exception|fail)|Mixin apply for mod vkselect failed' "$LOADER/run/logs/latest.log" 2>/dev/null | head -5
echo "=== game still running (expect 0): $(pgrep -fc '[d]evlaunchinjector' 2>/dev/null || echo 0) ==="
