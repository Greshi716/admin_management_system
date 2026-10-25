#!/usr/bin/env bash
# Runs the build and tests. Used by developers, Claude Code and CI.
set -euo pipefail
cd "$(dirname "$0")/.."

java_cmd=java
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && java_cmd="$JAVA_HOME/bin/java"
java_major=$("$java_cmd" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
if [ -z "$java_major" ] || [ "$java_major" -lt 21 ]; then
  echo "Java 21 or newer is required, but found: ${java_major:-none} (JAVA_HOME=${JAVA_HOME:-not set}). Set JAVA_HOME to a JDK 21." >&2
  exit 1
fi

echo "== backend: build and test =="
(cd management && ./mvnw -B verify)

if [ -f frontend/package.json ]; then
  echo "== frontend: install, build and test =="
  (cd frontend && npm ci && npm run build && npm test -- --watch=false)

  echo "== frontend: browser tests (start their own backend on port 8080) =="
  if (exec 3<>/dev/tcp/127.0.0.1/8080) 2>/dev/null; then
    echo "Port 8080 is in use. Stop the running backend, then run verify again." >&2
    exit 1
  fi
  (cd frontend && npm run e2e)
fi

echo "== FR coverage (strict: every requirement needs a test) =="
bash scripts/check-fr-coverage.sh --strict

echo "VERIFY OK"
