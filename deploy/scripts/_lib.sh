#!/usr/bin/env bash
# Shared helpers for deploy.sh / rollback.sh / backup-postgres.sh / restore-postgres.sh.
# Not meant to be run directly - sourced by the other scripts in this directory.
#
# Every script here takes an environment directory as its first argument (e.g. /opt/kcpc-dev or
# /opt/kcpc-prod) and derives the environment name, Compose service names, etc. from that
# directory's basename - it must be named exactly "kcpc-dev" or "kcpc-prod" (matching the target
# VM layout in deploy/README.md) for the derived service names (kcpc-dev-app / kcpc-prod-app) to
# resolve to the real Compose service keys in that environment's docker-compose.yml.

set -euo pipefail

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

require_env_dir() {
    local dir="$1"
    [[ -n "$dir" ]] || die "usage: $0 <env-dir> [...]"
    [[ -d "$dir" ]] || die "environment directory does not exist: $dir"
    [[ -f "$dir/docker-compose.yml" ]] || die "no docker-compose.yml in $dir - is this really an environment directory?"
    [[ -f "$dir/.env" ]] || die "no .env in $dir - copy .env.example to .env and fill in real values first"
}

env_name_of() { basename "$1"; }               # /opt/kcpc-dev -> kcpc-dev
app_service_of() { echo "$(env_name_of "$1")-app"; }
postgres_service_of() { echo "$(env_name_of "$1")-postgres"; }

# All Compose invocations go through this - keeps --env-file / -f consistent everywhere and
# means every script here only ever needs to pass the env dir, never re-derive Compose flags.
compose() {
    local dir="$1"; shift
    docker compose --env-file "$dir/.env" -f "$dir/docker-compose.yml" "$@"
}

current_image_tag() {
    local dir="$1"
    grep -E '^IMAGE_TAG=' "$dir/.env" | head -1 | cut -d= -f2-
}

set_image_tag() {
    local dir="$1" tag="$2"
    if grep -qE '^IMAGE_TAG=' "$dir/.env"; then
        # BSD (macOS) and GNU sed both accept this form with an explicit empty backup suffix.
        sed -i.bak -E "s/^IMAGE_TAG=.*/IMAGE_TAG=${tag}/" "$dir/.env" && rm -f "$dir/.env.bak"
    else
        printf '\nIMAGE_TAG=%s\n' "$tag" >> "$dir/.env"
    fi
}

# Waits for the app container's OWN healthcheck to report healthy, polling docker's view of it
# directly rather than curling through nginx - this is deliberately independent of whether nginx
# itself is mid-restart, so it never gives a false negative/positive because of the proxy layer.
wait_for_app_healthy() {
    local dir="$1" service; service="$(app_service_of "$dir")"
    local attempts=30
    for ((i = 1; i <= attempts; i++)); do
        local cid status
        cid="$(compose "$dir" ps -q "$service" || true)"
        if [[ -n "$cid" ]]; then
            status="$(docker inspect --format '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo "unknown")"
            if [[ "$status" == "healthy" ]]; then
                log "$service is healthy (attempt $i/$attempts)"
                return 0
            fi
            log "$service status=$status (attempt $i/$attempts)"
        else
            log "$service container not found yet (attempt $i/$attempts)"
        fi
        sleep 5
    done
    return 1
}
