#!/usr/bin/env bash
# Build Gradle sur le serveur de build (voir scripts/remote.py). Exemple : scripts/remote_build.sh :app:testDebugUnitTest
set -euo pipefail
cd "$(dirname "$0")/.."
exec python scripts/remote.py "$@"
