#!/usr/bin/env bash
# Repair common local Compose startup failures without deleting the database volume.
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker is not installed or is not on PATH." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Compose v2 is unavailable. Install the Docker Compose plugin." >&2
  exit 1
fi
if [[ ! -f .env ]]; then
  echo "ERROR: .env was not found. Run: cp .env.example .env, then set DB_PASSWORD." >&2
  exit 1
fi

# Read only DB_PASSWORD; this avoids executing arbitrary content from .env.
DB_PASSWORD="$(sed -n 's/^DB_PASSWORD=//p' .env | tail -n 1 | tr -d '\r')"
if [[ -z "$DB_PASSWORD" || "$DB_PASSWORD" == replace-with-* ]]; then
  echo "ERROR: Set DB_PASSWORD in .env before running this script." >&2
  echo "Generate a simple strong local password with: openssl rand -hex 24" >&2
  exit 1
fi
# The SQL below is deliberately restricted to safe characters. Hex output from
# openssl rand -hex is recommended in README.md and avoids SQL-quoting ambiguity.
if [[ ! "$DB_PASSWORD" =~ ^[A-Za-z0-9_.~-]+$ ]]; then
  echo "ERROR: DB_PASSWORD must contain only letters, numbers, dot, underscore, tilde, or hyphen for this recovery script." >&2
  echo "Generate a safe value with: openssl rand -hex 24, and update DB_PASSWORD in .env." >&2
  exit 1
fi

echo "[1/5] Starting/recreating PostgreSQL container (database volume will be preserved)..."
docker compose up -d --force-recreate postgres
POSTGRES_CID="$(docker compose ps -q postgres)"
if [[ -z "$POSTGRES_CID" ]]; then
  echo "ERROR: Compose did not return a PostgreSQL container ID." >&2
  docker compose logs --tail=80 postgres || true
  exit 1
fi

echo "[2/5] Waiting for PostgreSQL health check..."
DB_READY=0
for _ in $(seq 1 60); do
  if [[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$POSTGRES_CID" 2>/dev/null || true)" == "healthy" ]]; then
    DB_READY=1
    break
  fi
  sleep 2
done
if [[ "$DB_READY" -ne 1 ]]; then
  echo "ERROR: PostgreSQL did not become healthy. Latest database logs:" >&2
  docker compose logs --tail=120 postgres || true
  exit 1
fi

echo "[3/5] Synchronising the PostgreSQL role password to DB_PASSWORD in .env..."
# The official PostgreSQL image permits local socket administration from inside
# the container. This changes the role password in-place and does not drop data.
docker compose exec -T postgres sh -ec \
  'printf "ALTER ROLE solarfleet WITH PASSWORD '\''%s'\'';\\n" "$POSTGRES_PASSWORD" | psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'

echo "[4/5] Rebuilding and starting the application..."
docker compose up --build -d app
APP_CID="$(docker compose ps -q app)"
if [[ -z "$APP_CID" ]]; then
  echo "ERROR: Compose did not return an application container ID." >&2
  docker compose logs --tail=120 app || true
  exit 1
fi

echo "[5/5] Waiting for application health (up to 120 seconds)..."
APP_HEALTHY=0
for _ in $(seq 1 60); do
  STATUS="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$APP_CID" 2>/dev/null || true)"
  if [[ "$STATUS" == "healthy" ]]; then
    APP_HEALTHY=1
    break
  fi
  if [[ "$STATUS" == "unhealthy" || "$STATUS" == "exited" ]]; then
    break
  fi
  sleep 2
done

if [[ "$APP_HEALTHY" -eq 1 ]]; then
  echo
  echo "SUCCESS: Solar Fleet is responding at http://localhost:8080"
  curl -fsS http://127.0.0.1:8080/actuator/health || true
  echo
  exit 0
fi

echo "ERROR: The app is still not healthy. This script preserved the database." >&2
echo "Container state:" >&2
docker inspect --format 'Status={{.State.Status}} ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} RestartCount={{.RestartCount}} Error={{.State.Error}}' "$APP_CID" >&2 || true
echo >&2
echo "Application logs:" >&2
docker compose logs --tail=180 app >&2 || true
echo >&2
echo "PostgreSQL logs:" >&2
docker compose logs --tail=80 postgres >&2 || true
exit 1
