#!/usr/bin/env bash
# Scheduled Postgres backup: pg_dump -> gzip -> upload to GCS. Intended to run via cron/systemd
# timer on the VM (see deploy/README.md "Database backup" for the recommended schedule and the
# exact crontab line). The dump never stays only on the VM - it is deleted locally as soon as the
# GCS upload is confirmed, so a lost/corrupted VM disk never means a lost backup.
#
# Retention is handled by a GCS bucket lifecycle policy (set once, out-of-band - see
# deploy/README.md), not by this script, so there is one authoritative retention rule instead of
# scattering "delete after N days" logic across every script that touches the bucket.
#
# Usage:
#   backup-postgres.sh <env-dir> <gcs-bucket-uri>
#
# Example (typically only ever run against PROD, but works for either environment):
#   deploy/scripts/backup-postgres.sh /opt/kcpc-prod gs://kcpc-prod-db-backups

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

ENV_DIR="${1:-}"
BUCKET="${2:-}"
require_env_dir "$ENV_DIR"
[[ -n "$BUCKET" ]] || die "usage: $0 <env-dir> <gcs-bucket-uri>  (e.g. gs://kcpc-prod-db-backups)"
command -v gsutil >/dev/null 2>&1 || die "gsutil not found - install the Google Cloud SDK on this VM first"

PG_SERVICE="$(postgres_service_of "$ENV_DIR")"
ENV_NAME="$(env_name_of "$ENV_DIR")"
DB_NAME="$(grep -E '^DB_NAME=' "$ENV_DIR/.env" | head -1 | cut -d= -f2-)"
[[ -n "$DB_NAME" ]] || die "$ENV_DIR/.env has no DB_NAME set"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="/tmp/${ENV_NAME}_${DB_NAME}_${TIMESTAMP}.sql.gz"

log "Dumping $DB_NAME from $PG_SERVICE..."
# kcpc_migrator (schema owner) - never kcpc_app (the restricted runtime role) - a full logical
# dump needs to read every table, including ones kcpc_app has no SELECT on by design (DB-001).
compose "$ENV_DIR" exec -T "$PG_SERVICE" pg_dump -U kcpc_migrator -d "$DB_NAME" | gzip > "$DUMP_FILE"

SIZE="$(du -h "$DUMP_FILE" | cut -f1)"
log "Dump complete: $DUMP_FILE ($SIZE)"

log "Uploading to $BUCKET/postgres/..."
gsutil -q cp "$DUMP_FILE" "$BUCKET/postgres/$(basename "$DUMP_FILE")"

log "Upload confirmed - removing local copy (the VM is never the only place this backup lives)"
rm -f "$DUMP_FILE"

log "=== Backup complete: $BUCKET/postgres/$(basename "$DUMP_FILE") ==="
