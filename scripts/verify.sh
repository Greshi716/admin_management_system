#!/usr/bin/env bash
# Runs the build and tests. Used by developers, Claude Code and CI.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== backend: build and test =="
(cd management && ./mvnw -B verify)

if [ -f frontend/package.json ]; then
  echo "== frontend: install, build and test =="
  (cd frontend && npm ci && npm run build && npm test -- --watch=false)
fi

echo "== FR coverage (report only; strict mode is switched on in T099) =="
bash scripts/check-fr-coverage.sh

echo "VERIFY OK"
