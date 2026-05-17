#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE_DIR="${REMOTE_DIR:-/opt/fitness-bot}"
GOOGLE_DRIVE_DIR="${GOOGLE_DRIVE_DIR:-}"
RSYNC_ROOT="${RSYNC_ROOT:-}"
DEST_SUBDIR="${DEST_SUBDIR:-FitnessBot/backup/db}"
BACKUP_RSYNC_OPTIONS="${BACKUP_RSYNC_OPTIONS:--az --mkpath}"
RUN_NOW="${RUN_NOW:-0}"

usage() {
  cat <<USAGE
Usage: $0 [options]

Enable rsync copy of Fitness Bot PostgreSQL backups to Google Drive.
The destination folder is <rsync root>/${DEST_SUBDIR}.

Options:
  --rsync-root TARGET       Existing rsync root target, local path or remote TARGET
  --google-drive-dir PATH   Alias for --rsync-root when Google Drive is mounted locally
  --dir PATH                App directory. Default: ${REMOTE_DIR}
  --subdir PATH             Destination subfolder. Default: ${DEST_SUBDIR}
  --rsync-options OPTIONS   Rsync options. Default: ${BACKUP_RSYNC_OPTIONS}
  --run-now                 Run one backup after enabling
  -h, --help                Show this help

Examples:
  sudo $0 --rsync-root user@example.com:/GoogleDrive --run-now
  sudo $0 --rsync-root /mnt/google-drive --run-now

Environment overrides:
  REMOTE_DIR=/opt/fitness-bot
  GOOGLE_DRIVE_DIR=/mnt/google-drive
  RSYNC_ROOT=user@example.com:/GoogleDrive
  DEST_SUBDIR=FitnessBot/backup/db
  BACKUP_RSYNC_OPTIONS="-az --mkpath"
  RUN_NOW=1
USAGE
}

fail() {
  echo "$*" >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

shell_quote() {
  local value="$1"
  value="${value//\'/\'\\\'\'}"
  printf "'%s'" "$value"
}

join_target() {
  local root="${1%/}"
  local subdir="${2#/}"
  printf '%s/%s\n' "$root" "$subdir"
}

is_remote_rsync_target() {
  [[ "$1" == *:* && "$1" != /* ]]
}

detect_google_drive_dir() {
  local -a candidates=(
    "$HOME/Google Drive"
    "$HOME/GoogleDrive"
    "$HOME/google-drive"
    "$HOME/gdrive"
    "/mnt/google-drive"
    "/mnt/googledrive"
    "/mnt/gdrive"
    "/media/$USER/Google Drive"
    "/media/$USER/GoogleDrive"
  )

  for candidate in "${candidates[@]}"; do
    if [[ -d "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

set_env_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  local tmp
  local line

  line="${key}=$(shell_quote "$value")"
  tmp="$(mktemp)"

  if [[ -f "$file" ]]; then
    awk -v key="$key" -v line="$line" '
      BEGIN { found = 0; prefix = key "=" }
      index($0, prefix) == 1 {
        print line
        found = 1
        next
      }
      { print }
      END {
        if (!found) {
          print line
        }
      }
    ' "$file" > "$tmp"
  else
    printf '%s\n' "$line" > "$tmp"
  fi

  install -m 0600 "$tmp" "$file"
  rm -f "$tmp"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --google-drive-dir)
      GOOGLE_DRIVE_DIR="${2:?Missing value for --google-drive-dir}"
      shift 2
      ;;
    --rsync-root)
      RSYNC_ROOT="${2:?Missing value for --rsync-root}"
      shift 2
      ;;
    --dir)
      REMOTE_DIR="${2:?Missing value for --dir}"
      shift 2
      ;;
    --subdir)
      DEST_SUBDIR="${2:?Missing value for --subdir}"
      shift 2
      ;;
    --rsync-options)
      BACKUP_RSYNC_OPTIONS="${2:?Missing value for --rsync-options}"
      shift 2
      ;;
    --run-now)
      RUN_NOW=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_command rsync

if [[ -n "$GOOGLE_DRIVE_DIR" && -n "$RSYNC_ROOT" ]]; then
  fail "Use either --google-drive-dir or --rsync-root, not both"
fi

deploy_dir="$REMOTE_DIR/deploy"
backup_env="$deploy_dir/backup.env"
backup_env_example="$deploy_dir/backup.env.example"
backup_script="$deploy_dir/backup-postgres.sh"

[[ -f "$backup_script" ]] || fail "Missing backup script: $backup_script"

if [[ ! -f "$backup_env" && -f "$backup_env_example" ]]; then
  install -m 0600 "$backup_env_example" "$backup_env"
fi

if [[ -z "$GOOGLE_DRIVE_DIR" && -z "$RSYNC_ROOT" ]]; then
  GOOGLE_DRIVE_DIR="$(detect_google_drive_dir || true)"
fi

if [[ -z "$GOOGLE_DRIVE_DIR" && -z "$RSYNC_ROOT" ]]; then
  cat >&2 <<EOF
Missing rsync root target.

Run again with one of:
  sudo $0 --rsync-root user@example.com:/path/to/google-drive
  sudo $0 --rsync-root /path/to/google-drive
EOF
  exit 1
fi

if [[ -n "$GOOGLE_DRIVE_DIR" ]]; then
  [[ -d "$GOOGLE_DRIVE_DIR" ]] || fail "Google Drive root does not exist: $GOOGLE_DRIVE_DIR"
  rsync_target="$(join_target "$GOOGLE_DRIVE_DIR" "$DEST_SUBDIR")"
  mkdir -p "$rsync_target"
else
  rsync_target="$(join_target "$RSYNC_ROOT" "$DEST_SUBDIR")"
  if ! is_remote_rsync_target "$rsync_target"; then
    mkdir -p "$rsync_target"
  fi
fi

set_env_value "$backup_env" BACKUP_RSYNC_TARGET "$rsync_target"
set_env_value "$backup_env" BACKUP_RSYNC_OPTIONS "$BACKUP_RSYNC_OPTIONS"

chmod +x "$backup_script"

if command -v systemctl >/dev/null 2>&1 \
  && systemctl list-unit-files --no-legend fitness-bot-postgres-backup.timer 2>/dev/null | grep -q .; then
  systemctl restart fitness-bot-postgres-backup.timer
fi

echo "Configured PostgreSQL backup rsync target:"
echo "  $rsync_target"
echo
echo "Destination folder:"
echo "  ${DEST_SUBDIR}"

if [[ "$RUN_NOW" == "1" ]]; then
  if command -v systemctl >/dev/null 2>&1 \
    && systemctl list-unit-files --no-legend fitness-bot-postgres-backup.service 2>/dev/null | grep -q .; then
    systemctl start fitness-bot-postgres-backup.service
    journalctl -u fitness-bot-postgres-backup.service -n 80 --no-pager
  else
    "$backup_script"
  fi
fi
