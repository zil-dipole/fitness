#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE="${REMOTE:-lev@raspberrypi.local}"
REMOTE_DIR="${REMOTE_DIR:-/opt/fitness-bot}"
SSH_CONNECT_TIMEOUT="${SSH_CONNECT_TIMEOUT:-10}"
TELEGRAM_USERNAME="${TELEGRAM_USERNAME:-mghostl}"
TELEGRAM_ID="${TELEGRAM_ID:-}"
USE_AI_PARSER="${USE_AI_PARSER:-true}"

usage() {
  cat <<USAGE
Usage: $0 [options]

Set users.use_ai_parser through the admin HTTP API on the Raspberry Pi deployment.
Defaults target @mghostl and enable AI parsing.

Options:
  --remote USER@HOST       SSH target. Default: ${REMOTE}
  --dir PATH               Remote app directory. Default: ${REMOTE_DIR}
  --username USER          Telegram username, with or without @. Default: ${TELEGRAM_USERNAME}
  --telegram-id ID         Telegram numeric user ID. Calls the ID-based admin endpoint.
  --enabled true|false     Desired use_ai_parser value. Default: ${USE_AI_PARSER}
  --ssh-timeout SECONDS    SSH connect timeout. Default: ${SSH_CONNECT_TIMEOUT}
  -h, --help               Show this help

Environment overrides:
  REMOTE=lev@raspberrypi.local
  REMOTE_DIR=/opt/fitness-bot
  TELEGRAM_USERNAME=mghostl
  TELEGRAM_ID=123456789
  USE_AI_PARSER=true
  SSH_CONNECT_TIMEOUT=10
USAGE
}

fail() {
  echo "$*" >&2
  exit 1
}

normalize_bool() {
  case "$1" in
    true|TRUE|1|yes|YES|on|ON)
      echo "true"
      ;;
    false|FALSE|0|no|NO|off|OFF)
      echo "false"
      ;;
    *)
      fail "Invalid boolean value for --enabled/USE_AI_PARSER: $1"
      ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --remote)
      REMOTE="${2:?Missing value for --remote}"
      shift 2
      ;;
    --dir)
      REMOTE_DIR="${2:?Missing value for --dir}"
      shift 2
      ;;
    --username)
      TELEGRAM_USERNAME="${2:?Missing value for --username}"
      shift 2
      ;;
    --telegram-id)
      TELEGRAM_ID="${2:?Missing value for --telegram-id}"
      shift 2
      ;;
    --enabled)
      USE_AI_PARSER="${2:?Missing value for --enabled}"
      shift 2
      ;;
    --ssh-timeout)
      SSH_CONNECT_TIMEOUT="${2:?Missing value for --ssh-timeout}"
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

USE_AI_PARSER="$(normalize_bool "$USE_AI_PARSER")"
TELEGRAM_USERNAME="${TELEGRAM_USERNAME#@}"

if [[ -n "$TELEGRAM_USERNAME" && ! "$TELEGRAM_USERNAME" =~ ^[A-Za-z0-9_]{5,32}$ ]]; then
  fail "Telegram username must contain only letters, numbers, and underscores: $TELEGRAM_USERNAME"
fi

if [[ -n "$TELEGRAM_ID" && ! "$TELEGRAM_ID" =~ ^-?[0-9]+$ ]]; then
  fail "Telegram ID must be numeric: $TELEGRAM_ID"
fi

if [[ -z "$TELEGRAM_ID" && -z "$TELEGRAM_USERNAME" ]]; then
  fail "Provide --telegram-id or --username"
fi

if ! command -v ssh >/dev/null 2>&1; then
  fail "Missing required command: ssh"
fi

SSH_OPTS=(
  -o "ConnectTimeout=${SSH_CONNECT_TIMEOUT}"
  -o ConnectionAttempts=1
  -o ServerAliveInterval=15
  -o ServerAliveCountMax=2
)

remote_cmd="$(
  printf 'REMOTE_DIR=%q TELEGRAM_USERNAME=%q TELEGRAM_ID=%q USE_AI_PARSER=%q bash -s' \
    "$REMOTE_DIR" "$TELEGRAM_USERNAME" "$TELEGRAM_ID" "$USE_AI_PARSER"
)"

echo "Connecting to ${REMOTE}..."
ssh "${SSH_OPTS[@]}" "$REMOTE" "$remote_cmd" <<'REMOTE_SCRIPT'
set -Eeuo pipefail

fail() {
  echo "$*" >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command on remote host: $1"
  fi
}

update_parser_flag() {
  local enabled="$1"
  local admin_username="${ADMIN_USERNAME:-admin}"
  local admin_password="${ADMIN_PASSWORD:-}"
  local app_port="${APP_HTTP_PORT:-8080}"
  local endpoint
  local response
  local status
  local body

  [[ -n "$admin_password" ]] || fail "ADMIN_PASSWORD is required in ${REMOTE_DIR}/deploy/.env"

  if [[ -n "${TELEGRAM_ID:-}" ]]; then
    endpoint="http://127.0.0.1:${app_port}/admin/users/${TELEGRAM_ID}/parser"
  else
    endpoint="http://127.0.0.1:${app_port}/admin/users/by-login/${TELEGRAM_USERNAME}/parser"
  fi

  response="$(
    curl --silent --show-error --connect-timeout 5 --max-time 20 \
      --user "${admin_username}:${admin_password}" \
      --request PUT \
      "$endpoint" \
      --header "Content-Type: application/json" \
      --data "{\"useAiParser\":${enabled}}" \
      --write-out $'\n%{http_code}'
  )"

  response="${response//$'\r'/}"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"

  if [[ ! "$status" =~ ^2[0-9][0-9]$ ]]; then
    echo "Admin API returned HTTP ${status}." >&2
    [[ -z "$body" ]] || printf '%s\n' "$body" >&2
    return 1
  fi

  printf '%s\n' "$body"
}

require_command curl

cd "${REMOTE_DIR}/deploy" || fail "Remote deploy directory not found: ${REMOTE_DIR}/deploy"

if [[ ! -f .env ]]; then
  fail "Remote env file not found: ${REMOTE_DIR}/deploy/.env"
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

if [[ "${USE_AI_PARSER}" != "true" && "${USE_AI_PARSER}" != "false" ]]; then
  fail "USE_AI_PARSER must be true or false"
fi

if [[ -n "${TELEGRAM_ID:-}" ]]; then
  echo "Calling admin API to set use_ai_parser=${USE_AI_PARSER} for telegram_id=${TELEGRAM_ID}..."
else
  echo "Calling admin API to set use_ai_parser=${USE_AI_PARSER} for @${TELEGRAM_USERNAME}..."
fi

if ! update_result="$(update_parser_flag "$USE_AI_PARSER")"; then
  cat >&2 <<EOF
Admin API did not update use_ai_parser.
If you used a login, make sure @${TELEGRAM_USERNAME} has interacted with the bot after the deployment that stores Telegram usernames.
EOF
  exit 1
fi

echo "Admin API response:"
printf '%s\n' "$update_result"
REMOTE_SCRIPT
