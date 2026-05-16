#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app_dir="$(cd "$script_dir/.." && pwd)"

ENV_FILE="${ENV_FILE:-$script_dir/.env}"
BACKUP_ENV_FILE="${BACKUP_ENV_FILE:-$script_dir/backup.env}"

fail() {
  echo "$*" >&2
  exit 1
}

load_env_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    set -a
    # shellcheck disable=SC1090
    . "$file"
    set +a
  fi
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

delete_old_backups() {
  local dir="$1"
  local retention_days="$2"
  [[ "$retention_days" =~ ^[0-9]+$ ]] || fail "Retention days must be numeric: $retention_days"
  [[ "$retention_days" -gt 0 ]] || fail "Retention days must be greater than zero: $retention_days"

  find "$dir" -maxdepth 1 -type f \
    \( -name 'fitness_bot_postgres_*.dump' -o -name 'fitness_bot_postgres_*.dump.sha256' \) \
    -mtime "+${retention_days}" -delete
}

load_env_file "$ENV_FILE"
load_env_file "$BACKUP_ENV_FILE"

POSTGRES_DB="${POSTGRES_DB:-fitness_bot}"
POSTGRES_USER="${POSTGRES_USER:-fitness_bot}"
BACKUP_DIR="${BACKUP_DIR:-$app_dir/backups/postgres}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

require_command docker
require_command sha256sum

mkdir -p "$BACKUP_DIR"
cd "$script_dir"

if [[ -z "$(docker compose ps -q postgres 2>/dev/null)" ]]; then
  fail "Postgres compose service is not available. Run this from a deployed host with docker compose up."
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_name="fitness_bot_postgres_${timestamp}.dump"
backup_file="$BACKUP_DIR/$backup_name"
tmp_file="$BACKUP_DIR/.${backup_name}.tmp"
checksum_file="$backup_file.sha256"

cleanup() {
  rm -f "$tmp_file"
}
trap cleanup EXIT

echo "Creating PostgreSQL backup: $backup_file"

docker compose exec -T postgres pg_dump \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  --format=custom \
  --no-owner \
  --no-acl \
  > "$tmp_file"

mv "$tmp_file" "$backup_file"
(cd "$BACKUP_DIR" && sha256sum "$backup_name" > "$(basename "$checksum_file")")

cp "$backup_file" "$BACKUP_DIR/latest.dump"
(cd "$BACKUP_DIR" && sha256sum latest.dump > latest.dump.sha256)
delete_old_backups "$BACKUP_DIR" "$BACKUP_RETENTION_DAYS"

echo "Backup complete: $backup_file"
