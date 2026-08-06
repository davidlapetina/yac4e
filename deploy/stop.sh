#!/usr/bin/env bash
# Stop YaC4e. Data is kept unless --wipe is passed.
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

if [[ "${1:-}" == "--wipe" ]]; then
  echo "This deletes the database volume and every workspace stored in it."
  read -r -p "Type 'wipe' to confirm: " answer
  if [[ "$answer" != "wipe" ]]; then
    echo "Cancelled; nothing was removed."
    exit 1
  fi
  docker compose down -v
  echo "Stopped and database volume removed."
  exit 0
fi

docker compose down
echo "Stopped. The database volume is retained; run ./start.sh to bring it back."
