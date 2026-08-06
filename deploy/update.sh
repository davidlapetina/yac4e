#!/usr/bin/env bash
# Pull the latest code and restart with a freshly built image. Credentials and data are kept.
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")/.."

echo "Updating from git..."
git pull --ff-only

cd deploy
echo "Rebuilding and restarting..."
exec ./start.sh --rebuild
