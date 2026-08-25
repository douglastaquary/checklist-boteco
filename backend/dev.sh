#!/usr/bin/env bash
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$BACKEND_DIR/.." && pwd)"

if [[ -f "$BACKEND_DIR/.env.local" ]]; then
  set -a
  source "$BACKEND_DIR/.env.local"
  set +a
elif [[ -f "$ROOT_DIR/.env.local" ]]; then
  set -a
  source "$ROOT_DIR/.env.local"
  set +a
fi

cd "$BACKEND_DIR"
exec ./mvnw quarkus:dev "$@"
