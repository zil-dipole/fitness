#!/usr/bin/env bash
set -Eeuo pipefail

REMOTE="${REMOTE:-lev@raspberrypi.local}"
REMOTE_DIR="${REMOTE_DIR:-/opt/fitness-bot}"
RUN_TESTS="${RUN_TESTS:-1}"
INSTALL_SYSTEMD="${INSTALL_SYSTEMD:-0}"
SSH_CONNECT_TIMEOUT="${SSH_CONNECT_TIMEOUT:-10}"
APP_IMAGE="${APP_IMAGE:-}"
BUILD_IMAGE="${BUILD_IMAGE:-1}"
IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-mghostl/fitness-bot}"
IMAGE_TAG="${IMAGE_TAG:-}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/arm64}"

usage() {
  cat <<USAGE
Usage: $0 [options]

Build, push, and deploy the fitness bot to a Linux host over SSH.
The app container is always pulled on the remote host; it is not built there.

Options:
  --remote USER@HOST       SSH target. Default: ${REMOTE}
  --dir PATH               Remote app directory. Default: ${REMOTE_DIR}
  --image IMAGE            Deploy an already-pushed image and skip local image build/push
  --repository NAME        Image repository for local build/push. Default: ${IMAGE_REPOSITORY}
  --tag TAG                Image tag for local build/push. Default: YYYYMMDD-<git-sha>
  --platform OS/ARCH       Image platform for local build/push. Default: ${IMAGE_PLATFORM}
  --no-build-image         Skip local image build/push and use APP_IMAGE from deploy/.env
  --skip-tests             Skip local 'mvn test' before deployment
  --install-systemd        Install and enable the systemd service on the remote host
  --ssh-timeout SECONDS    SSH connect timeout. Default: ${SSH_CONNECT_TIMEOUT}
  -h, --help               Show this help

Environment overrides:
  REMOTE=lev@raspberrypi.local
  REMOTE_DIR=/opt/fitness-bot
  IMAGE_REPOSITORY=mghostl/fitness-bot
  IMAGE_TAG=20260427-abcdef123456
  IMAGE_PLATFORM=linux/arm64
  BUILD_IMAGE=0
  APP_IMAGE=mghostl/fitness-bot:20260427-abcdef123456
  RUN_TESTS=0
  INSTALL_SYSTEMD=1
  SSH_CONNECT_TIMEOUT=10
USAGE
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
    --image)
      APP_IMAGE="${2:?Missing value for --image}"
      BUILD_IMAGE=0
      shift 2
      ;;
    --repository)
      IMAGE_REPOSITORY="${2:?Missing value for --repository}"
      shift 2
      ;;
    --tag)
      IMAGE_TAG="${2:?Missing value for --tag}"
      shift 2
      ;;
    --platform)
      IMAGE_PLATFORM="${2:?Missing value for --platform}"
      shift 2
      ;;
    --no-build-image)
      BUILD_IMAGE=0
      shift
      ;;
    --skip-tests)
      RUN_TESTS=0
      shift
      ;;
    --install-systemd)
      INSTALL_SYSTEMD=1
      shift
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

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
local_env_file="$repo_root/deploy/.env"
effective_env_file=""

cleanup() {
  if [[ -n "$effective_env_file" && -f "$effective_env_file" ]]; then
    rm -f "$effective_env_file"
  fi
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command ssh
require_command rsync

if [[ ! -f "$local_env_file" ]]; then
  cat >&2 <<EOF
Missing local deployment env file: $local_env_file

Create it locally before deployment:
  cp deploy/.env.example deploy/.env
  nano deploy/.env
EOF
  exit 1
fi

read_env_value() {
  local key="$1"
  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      print
      exit
    }
  ' "$local_env_file"
}

SSH_OPTS=(
  -o "ConnectTimeout=${SSH_CONNECT_TIMEOUT}"
  -o ConnectionAttempts=1
  -o ServerAliveInterval=15
  -o ServerAliveCountMax=2
)

ssh_cmd() {
  ssh -t "${SSH_OPTS[@]}" "$REMOTE" "$@"
}

if [[ "$BUILD_IMAGE" == "1" ]]; then
  require_command git

  if [[ ! -x "$repo_root/deploy/build-push-image.sh" ]]; then
    echo "Missing executable build script: $repo_root/deploy/build-push-image.sh" >&2
    exit 1
  fi

  if [[ -z "$IMAGE_TAG" ]]; then
    commit_hash="$(git -C "$repo_root" rev-parse --short=12 HEAD)"
    IMAGE_TAG="$(date +%Y%m%d)-${commit_hash}"
  fi

  APP_IMAGE="${IMAGE_REPOSITORY}:${IMAGE_TAG}"
  build_args=(
    --repository "$IMAGE_REPOSITORY"
    --tag "$IMAGE_TAG"
    --platform "$IMAGE_PLATFORM"
  )

  if [[ "$RUN_TESTS" == "0" ]]; then
    build_args+=(--skip-tests)
  fi

  echo "Building and pushing deployment image: $APP_IMAGE"
  "$repo_root/deploy/build-push-image.sh" "${build_args[@]}"
else
  if [[ -z "$APP_IMAGE" ]]; then
    APP_IMAGE="$(read_env_value APP_IMAGE)"
  fi

  if [[ -z "$APP_IMAGE" ]]; then
    APP_IMAGE="mghostl/fitness-bot:latest"
  fi
fi

if [[ "$APP_IMAGE" == *[[:space:]]* ]]; then
  echo "Invalid APP_IMAGE contains whitespace: $APP_IMAGE" >&2
  exit 1
fi

effective_env_file="$(mktemp)"
awk -v image="$APP_IMAGE" '
  BEGIN { found = 0 }
  /^APP_IMAGE=/ {
    print "APP_IMAGE=" image
    found = 1
    next
  }
  { print }
  END {
    if (!found) {
      print "APP_IMAGE=" image
    }
  }
' "$local_env_file" > "$effective_env_file"
chmod 600 "$effective_env_file"

if [[ "$BUILD_IMAGE" != "1" && "$RUN_TESTS" == "1" ]]; then
  require_command mvn
  echo "Running local tests..."
  (cd "$repo_root" && mvn test)
fi

echo "Checking SSH connectivity to ${REMOTE}..."
if ! ssh_cmd 'printf "Connected to "; hostname'; then
  cat >&2 <<EOF
Cannot connect to ${REMOTE}.

Check manually:
  ssh ${REMOTE} 'hostname'

If the hostname is the standard Raspberry Pi spelling, try:
  $0 --remote lev@raspberrypi.local

You can also use the Pi IP address:
  $0 --remote lev@192.168.x.x
EOF
  exit 1
fi

echo "Checking remote Docker binary..."
ssh_cmd 'command -v docker'

echo "Checking remote Docker Compose plugin..."
ssh_cmd 'docker compose version'

echo "Checking remote Docker daemon access..."
if ! ssh_cmd 'docker info >/dev/null'; then
  cat >&2 <<EOF
Docker is installed, but ${REMOTE} cannot access the Docker daemon.

On the Raspberry Pi, either add the user to the docker group and reconnect:
  sudo usermod -aG docker lev

Or run deployment from a user that can execute:
  docker info
EOF
  exit 1
fi

echo "Preparing remote directory ${REMOTE_DIR}/deploy..."
ssh_cmd "sudo mkdir -p '$REMOTE_DIR/deploy' && sudo chown \"\$(id -un):\$(id -gn)\" '$REMOTE_DIR' '$REMOTE_DIR/deploy'"

echo "Deploying image: ${APP_IMAGE}"
echo "Syncing deployment files to ${REMOTE}:${REMOTE_DIR}/deploy..."
rsync -az --delete \
  -e "ssh -o ConnectTimeout=${SSH_CONNECT_TIMEOUT} -o ConnectionAttempts=1 -o ServerAliveInterval=15 -o ServerAliveCountMax=2" \
  --exclude '.env' \
  --exclude 'backup.env' \
  "$repo_root/deploy"/ "$REMOTE":"$REMOTE_DIR/deploy"/

rsync -az \
  -e "ssh -o ConnectTimeout=${SSH_CONNECT_TIMEOUT} -o ConnectionAttempts=1 -o ServerAliveInterval=15 -o ServerAliveCountMax=2" \
  "$effective_env_file" "$REMOTE":"$REMOTE_DIR/deploy/.env"

echo "Starting remote deployment..."
ssh "${SSH_OPTS[@]}" "$REMOTE" "REMOTE_DIR='$REMOTE_DIR' INSTALL_SYSTEMD='$INSTALL_SYSTEMD' bash -s" <<'REMOTE_SCRIPT'
set -Eeuo pipefail

cd "$REMOTE_DIR/deploy"

chmod 600 .env

printf 'Pulling app image: '
awk -F= '$1 == "APP_IMAGE" { print $2; found = 1 } END { if (!found) print "mghostl/fitness-bot:latest" }' .env

docker compose pull app postgres redis
docker compose up -d postgres redis app

printf 'Redis ping: '
docker compose exec -T redis redis-cli ping

if [[ "$INSTALL_SYSTEMD" == "1" ]]; then
  tmp_service="$(mktemp)"
  sed "s|^WorkingDirectory=.*|WorkingDirectory=$REMOTE_DIR/deploy|" \
    "$REMOTE_DIR/deploy/systemd/fitness-bot.service" > "$tmp_service"
  sudo install -m 0644 "$tmp_service" /etc/systemd/system/fitness-bot.service
  rm -f "$tmp_service"
  sudo systemctl daemon-reload
  sudo systemctl enable --now fitness-bot
fi

docker compose ps
REMOTE_SCRIPT

printf '%s\n' "Deployment finished."
printf '%s\n' "View logs with:"
printf '  ssh %s\n' "$REMOTE"
printf '  cd %s/deploy\n' "$REMOTE_DIR"
printf '%s\n' "  docker compose logs -f app"
