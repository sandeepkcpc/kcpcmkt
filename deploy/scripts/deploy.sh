#!/usr/bin/env bash
# Deploys a new image tag to one environment (DEV or PROD), touching only the app service -
# Postgres is never recreated/bounced for an ordinary release. Auto-rolls back to the previous
# tag if the new one fails its healthcheck, and exits non-zero either way so the calling GitHub
# Actions job can tell success from failure.
#
# Usage (run ON the VM, or remotely via `gcloud compute ssh ... -- 'bash -s' < deploy.sh args...`):
#   deploy.sh <env-dir> <new-image-tag>
#
# Example:
#   deploy/scripts/deploy.sh /opt/kcpc-dev  a1b2c3d4e5f6...   # git SHA
#   deploy/scripts/deploy.sh /opt/kcpc-prod a1b2c3d4e5f6...

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

ENV_DIR="${1:-}"
NEW_TAG="${2:-}"
require_env_dir "$ENV_DIR"
[[ -n "$NEW_TAG" ]] || die "usage: $0 <env-dir> <new-image-tag>"

APP_SERVICE="$(app_service_of "$ENV_DIR")"
ENV_NAME="$(env_name_of "$ENV_DIR")"
PREVIOUS_TAG="$(current_image_tag "$ENV_DIR" || true)"

log "=== Deploying $ENV_NAME: $PREVIOUS_TAG -> $NEW_TAG ==="

# Snapshot the whole .env (not just the tag) before touching anything, so a rollback restores
# every value exactly as it was, not just IMAGE_TAG.
cp "$ENV_DIR/.env" "$ENV_DIR/.env.previous"
set_image_tag "$ENV_DIR" "$NEW_TAG"

log "Pulling $APP_SERVICE image..."
compose "$ENV_DIR" pull "$APP_SERVICE"

log "Starting $APP_SERVICE only (--no-deps: Postgres is left untouched)..."
compose "$ENV_DIR" up -d --no-deps "$APP_SERVICE"

log "Waiting for $APP_SERVICE to report healthy..."
if wait_for_app_healthy "$ENV_DIR"; then
    log "=== Deploy succeeded: $ENV_NAME is now running $NEW_TAG ==="
    # Keep .env.previous around as the rollback target for the NEXT deploy attempt, and as an
    # audit trail of the last-known-good configuration - never deleted automatically.
    exit 0
fi

log "!!! Healthcheck failed for $NEW_TAG - rolling back to $PREVIOUS_TAG !!!"
cp "$ENV_DIR/.env.previous" "$ENV_DIR/.env"
compose "$ENV_DIR" pull "$APP_SERVICE" || true
compose "$ENV_DIR" up -d --no-deps "$APP_SERVICE" || true
if wait_for_app_healthy "$ENV_DIR"; then
    log "Rollback to $PREVIOUS_TAG succeeded - $ENV_NAME is stable again"
else
    log "!!! Rollback ALSO failed to become healthy - $ENV_NAME needs manual attention !!!"
fi
log "=== Deploy of $NEW_TAG FAILED ==="
exit 1
