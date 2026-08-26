#!/usr/bin/env bash
# Manual rollback - use when a bad deploy passed its healthcheck but turned out to be wrong in
# some way the healthcheck can't see (a real bug, not a startup failure). deploy.sh already
# auto-rolls back on a failed healthcheck; this script is for everything else.
#
# Usage:
#   rollback.sh <env-dir>                 # rolls back to whatever .env.previous records
#   rollback.sh <env-dir> <target-tag>     # rolls back to a specific, deterministic git SHA
#
# Examples:
#   deploy/scripts/rollback.sh /opt/kcpc-prod
#   deploy/scripts/rollback.sh /opt/kcpc-prod a1b2c3d4e5f6...

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

ENV_DIR="${1:-}"
TARGET_TAG="${2:-}"
require_env_dir "$ENV_DIR"

APP_SERVICE="$(app_service_of "$ENV_DIR")"
ENV_NAME="$(env_name_of "$ENV_DIR")"
CURRENT_TAG="$(current_image_tag "$ENV_DIR" || true)"

if [[ -z "$TARGET_TAG" ]]; then
    [[ -f "$ENV_DIR/.env.previous" ]] || die "no target tag given and no $ENV_DIR/.env.previous to roll back to"
    TARGET_TAG="$(grep -E '^IMAGE_TAG=' "$ENV_DIR/.env.previous" | head -1 | cut -d= -f2-)"
    [[ -n "$TARGET_TAG" ]] || die "$ENV_DIR/.env.previous has no IMAGE_TAG recorded"
fi

log "=== Rolling back $ENV_NAME: $CURRENT_TAG -> $TARGET_TAG ==="
cp "$ENV_DIR/.env" "$ENV_DIR/.env.pre-rollback"
set_image_tag "$ENV_DIR" "$TARGET_TAG"

compose "$ENV_DIR" pull "$APP_SERVICE"
compose "$ENV_DIR" up -d --no-deps "$APP_SERVICE"

if wait_for_app_healthy "$ENV_DIR"; then
    log "=== Rollback succeeded: $ENV_NAME is now running $TARGET_TAG ==="
    exit 0
else
    log "!!! Rollback target $TARGET_TAG ALSO failed its healthcheck - $ENV_NAME needs manual attention !!!"
    log "Its previous state (before this rollback attempt) was saved to $ENV_DIR/.env.pre-rollback"
    exit 1
fi
