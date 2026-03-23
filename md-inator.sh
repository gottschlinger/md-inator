#!/usr/bin/env bash
# md-inator wrapper — build (if needed) and run
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR=$(find "$SCRIPT_DIR/build/libs" -name "md-inator-*.jar" 2>/dev/null | sort -V | tail -1)

if [[ -z "$JAR" ]]; then
  echo "Building md-inator..."
  cd "$SCRIPT_DIR" && ./gradlew jar -q
  JAR=$(find "$SCRIPT_DIR/build/libs" -name "md-inator-*.jar" | sort -V | tail -1)
fi

exec java -jar "$JAR" "$@"
