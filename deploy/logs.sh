#!/usr/bin/env bash
# Follow logs. Pass a service name (app or postgres) to narrow, default is the app.
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"
exec docker compose logs -f --tail "${TAIL:-100}" "${1:-app}"
