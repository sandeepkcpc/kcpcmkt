#!/usr/bin/env bash
# Restores a backup produced by backup-postgres.sh. DESTRUCTIVE: overwrites every row currently
# in the target database. Requires an explicit --force flag precisely so this can never be run by
# accident (no interactive prompt, so it stays scriptable for real disaster recovery, but the
# flag itself is the deliberate confirmation).
#
# Usage:
#   restore-postgres.sh <env-dir> <gcs-object-path> --force
#
# Example:
#   deploy/scripts/restore-postgres.sh /opt/kcpc-prod \
#       gs://kcpc-prod-db-backups/postgres/kcpc-prod_kcpc_prod_20260101T030000Z.sql.gz --force

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

ENV_DIR="${1:-}"
OBJECT="${2:-}"
FORCE="${3:-}"
require_env_dir "$ENV_DIR"
[[ -n "$OBJECT" ]] || die "usage: $0 <env-dir> <gcs-object-path> --force"
[[ "$FORCE" == "--force" ]] || die "refusing to restore without --force - this OVERWRITES the current database. Re-run with --force once you are certain."
command -v gsutil >/dev/null 2>&1 || die "gsutil not found - install the Google Cloud SDK on this VM first"

PG_SERVICE="$(postgres_service_of "$ENV_DIR")"
DB_NAME="$(grep -E '^DB_NAME=' "$ENV_DIR/.env" | head -1 | cut -d= -f2-)"
[[ -n "$DB_NAME" ]] || die "$ENV_DIR/.env has no DB_NAME set"

LOCAL_FILE="/tmp/$(basename "$OBJECT")"
log "Downloading $OBJECT..."
gsutil -q cp "$OBJECT" "$LOCAL_FILE"

log "Restoring into $DB_NAME via $PG_SERVICE - this will overwrite existing data..."
gunzip -c "$LOCAL_FILE" | compose "$ENV_DIR" exec -T "$PG_SERVICE" psql -U kcpc_migrator -d "$DB_NAME"

rm -f "$LOCAL_FILE"
log "=== Restore complete: $DB_NAME restored from $(basename "$OBJECT") ==="
log "Restart the app service so any pooled/cached connections pick up the restored state: "
log "  deploy/scripts/deploy.sh $ENV_DIR \$(grep -E '^IMAGE_TAG=' $ENV_DIR/.env | cut -d= -f2-)"
