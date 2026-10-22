#!/usr/bin/env bash
set -euo pipefail

APP_IMAGE=${APP_IMAGE:?Set APP_IMAGE to a local image tag or immutable quality-reference-application reference}
APP_SOURCE_REVISION=${APP_SOURCE_REVISION:?Set APP_SOURCE_REVISION to the source commit baked into APP_IMAGE}
APP_FIXTURE_VERSION=${APP_FIXTURE_VERSION:-v0.1.0}
DB_IMAGE='postgres:18.6-alpine3.23@sha256:885cf05d376c7cf27afef02073e6bdac3841252537f16e244fd1c1e6a7c99fb1'
RUN_ID="aria-app-${GITHUB_RUN_ID:-local}-$(openssl rand -hex 4)"
NETWORK="${RUN_ID}-net"
DB_CONTAINER="${RUN_ID}-db"
APP_CONTAINER="${RUN_ID}-app"
APP_PORT=${APP_PORT:-4310}
NAMESPACE="${APP_NAMESPACE:-${RUN_ID,,}}"
POSTGRES_PASSWORD=$(openssl rand -hex 32)
TEST_CONTROL_TOKEN=$(openssl rand -hex 32)
APP_BASE_URL="http://127.0.0.1:${APP_PORT}"
export APP_BASE_URL APP_SOURCE_REVISION APP_FIXTURE_VERSION TEST_CONTROL_TOKEN

cleanup() {
  result=$?
  if [ "$result" -ne 0 ]; then
    docker logs "$APP_CONTAINER" || true
    docker logs "$DB_CONTAINER" || true
  fi
  docker rm --force "$APP_CONTAINER" "$DB_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  return "$result"
}
trap cleanup EXIT

if [ "${APP_IMAGE_LOCAL:-false}" = true ]; then
  docker image inspect "$APP_IMAGE" >/dev/null
else
  docker pull "$APP_IMAGE"
fi
docker pull "$DB_IMAGE"
docker network create "$NETWORK" >/dev/null
docker run --detach --name "$DB_CONTAINER" --network "$NETWORK" \
  --env POSTGRES_DB=reference_app --env POSTGRES_USER=reference_app \
  --env "POSTGRES_PASSWORD=${POSTGRES_PASSWORD}" \
  --health-cmd='pg_isready -U reference_app -d reference_app' \
  --health-interval=2s --health-timeout=2s --health-retries=30 "$DB_IMAGE" >/dev/null

for attempt in $(seq 1 60); do
  health=$(docker inspect --format '{{.State.Health.Status}}' "$DB_CONTAINER")
  if [ "$health" = healthy ]; then break; fi
  if [ "$health" = unhealthy ]; then docker logs "$DB_CONTAINER"; exit 1; fi
  sleep 2
done
[ "$(docker inspect --format '{{.State.Health.Status}}' "$DB_CONTAINER")" = healthy ] || {
  docker logs "$DB_CONTAINER"
  exit 1
}

docker run --detach --name "$APP_CONTAINER" --network "$NETWORK" \
  --publish "127.0.0.1:${APP_PORT}:4300" \
  --env NODE_ENV=production --env PORT=4300 \
  --env "DATABASE_URL=postgres://reference_app:${POSTGRES_PASSWORD}@${DB_CONTAINER}:5432/reference_app" \
  --env "APP_NAMESPACE=${NAMESPACE}" --env "APP_SOURCE_SHA=${APP_SOURCE_REVISION}" \
  --env "SEED_VERSION=${APP_FIXTURE_VERSION}" --env TEST_MODE=true \
  --env "TEST_CONTROL_TOKEN=${TEST_CONTROL_TOKEN}" "$APP_IMAGE" >/dev/null

for attempt in $(seq 1 60); do
  if curl --silent --show-error --fail "${APP_BASE_URL}/health/ready" > /tmp/aria-reference-ready.json; then break; fi
  sleep 2
done
node -e 'const fs=require("node:fs"); const ready=JSON.parse(fs.readFileSync("/tmp/aria-reference-ready.json","utf8")); if (ready.status !== "ready" || ready.sourceRevision !== process.env.APP_SOURCE_REVISION || ready.seedVersion !== process.env.APP_FIXTURE_VERSION || ready.datastore !== true) process.exit(1);'

"$@"
