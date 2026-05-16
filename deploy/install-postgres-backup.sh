#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE_DIR="${REMOTE_DIR:-/opt/fitness-bot}"
ON_CALENDAR="${ON_CALENDAR:-*-*-* 03:15:00}"

usage() {
  cat <<USAGE
Usage: $0 [options]

Install and enable the Fitness Bot PostgreSQL backup systemd timer on this host.
Run this on the Raspberry Pi after deploy files are synced.

Options:
  --dir PATH             App directory. Default: ${REMOTE_DIR}
  --calendar VALUE       systemd OnCalendar value. Default: ${ON_CALENDAR}
  -h, --help             Show this help

Environment overrides:
  REMOTE_DIR=/opt/fitness-bot
  ON_CALENDAR='*-*-* 03:15:00'
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)
      REMOTE_DIR="${2:?Missing value for --dir}"
      shift 2
      ;;
    --calendar)
      ON_CALENDAR="${2:?Missing value for --calendar}"
      shift 2
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

deploy_dir="$REMOTE_DIR/deploy"
service_template="$deploy_dir/systemd/fitness-bot-postgres-backup.service"
timer_template="$deploy_dir/systemd/fitness-bot-postgres-backup.timer"
backup_env="$deploy_dir/backup.env"
backup_env_example="$deploy_dir/backup.env.example"

if [[ ! -f "$service_template" || ! -f "$timer_template" ]]; then
  echo "Missing backup systemd templates under $deploy_dir/systemd" >&2
  exit 1
fi

if [[ ! -f "$backup_env" && -f "$backup_env_example" ]]; then
  cp "$backup_env_example" "$backup_env"
  chmod 600 "$backup_env"
  echo "Created $backup_env from backup.env.example"
fi

chmod +x "$deploy_dir/backup-postgres.sh"

service_tmp="$(mktemp)"
timer_tmp="$(mktemp)"
cleanup() {
  rm -f "$service_tmp" "$timer_tmp"
}
trap cleanup EXIT

sed \
  -e "s|^ConditionPathExists=.*|ConditionPathExists=$deploy_dir/compose.yml|" \
  -e "s|^WorkingDirectory=.*|WorkingDirectory=$deploy_dir|" \
  -e "s|^ExecStart=.*|ExecStart=$deploy_dir/backup-postgres.sh|" \
  "$service_template" > "$service_tmp"

sed \
  -e "s|^OnCalendar=.*|OnCalendar=$ON_CALENDAR|" \
  "$timer_template" > "$timer_tmp"

sudo install -m 0644 "$service_tmp" /etc/systemd/system/fitness-bot-postgres-backup.service
sudo install -m 0644 "$timer_tmp" /etc/systemd/system/fitness-bot-postgres-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now fitness-bot-postgres-backup.timer

echo "Installed and enabled fitness-bot-postgres-backup.timer"
systemctl list-timers fitness-bot-postgres-backup.timer --no-pager
