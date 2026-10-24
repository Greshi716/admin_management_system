#!/usr/bin/env bash
# Runs the build and tests. Used by developers, Claude Code and CI.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== backend: build and test =="
(cd management && ./mvnw -B verify)

if [ -f frontend/package.json ]; then
  echo "== frontend: build =="
  (cd frontend && npm run build)
fi

echo "VERIFY OK"
