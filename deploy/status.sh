#!/usr/bin/env bash
# Show what is running and whether the API answers.
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

docker compose ps

if [[ -f ./.env ]]; then
  # shellcheck disable=SC1091
  set -a; source ./.env; set +a
  PORT="${YAC4E_PORT:-8080}"
  code="$(curl -s -o /dev/null -w '%{http_code}' -u "${YAC4E_BASIC_USERNAME}:${YAC4E_BASIC_PASSWORD}" \
    "http://localhost:${PORT}/api/workspaces" 2>/dev/null || echo 000)"
  echo
  echo "GET /api/workspaces -> HTTP ${code}"
  if [[ "$code" == "200" ]]; then
    count="$(curl -s -u "${YAC4E_BASIC_USERNAME}:${YAC4E_BASIC_PASSWORD}" \
      "http://localhost:${PORT}/api/workspaces" | grep -o '"id"' | wc -l | tr -d ' ')"
    echo "workspaces: ${count}"
    echo "URL: http://$(hostname -I 2>/dev/null | awk '{print $1}'):${PORT}"
  fi
else
  echo
  echo "No .env yet: run ./start.sh first."
fi
