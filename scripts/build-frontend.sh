#!/usr/bin/env bash
# Builds the Angular app and copies it into the backend's static folder, so one jar serves the whole app.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== frontend: production build =="
(cd frontend && npm ci && npm run build)

target=management/src/main/resources/static
rm -rf "$target"
mkdir -p "$target"
cp -R frontend/dist/frontend/browser/. "$target/"
echo "Copied the build to $target"
