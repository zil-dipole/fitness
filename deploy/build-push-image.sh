#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-mghostl/fitness-bot}"
IMAGE_TAG="${IMAGE_TAG:-}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/arm64}"
RUN_TESTS="${RUN_TESTS:-1}"
PUSH_IMAGE="${PUSH_IMAGE:-1}"
UPDATE_ENV="${UPDATE_ENV:-0}"

usage() {
  cat <<USAGE
Usage: $0 [options]

Build the service Docker image locally with Maven/Jib and optionally push it.

Options:
  --repository NAME     Image repository. Default: ${IMAGE_REPOSITORY}
  --tag TAG             Image tag. Default: YYYYMMDD-<git-sha>
  --platform OS/ARCH    Image platform. Default: ${IMAGE_PLATFORM}
  --skip-tests          Skip local 'mvn test' before image build
  --no-push             Build locally but do not push
  --update-env          Set APP_IMAGE in deploy/.env after successful build/push
  -h, --help            Show this help

Environment overrides:
  IMAGE_REPOSITORY=mghostl/fitness-bot
  IMAGE_TAG=20260427-abcdef123456
  IMAGE_PLATFORM=linux/arm64
  RUN_TESTS=0
  PUSH_IMAGE=0
  UPDATE_ENV=1
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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
    --skip-tests)
      RUN_TESTS=0
      shift
      ;;
    --no-push)
      PUSH_IMAGE=0
      shift
      ;;
    --update-env)
      UPDATE_ENV=1
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

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command git
require_command mvn
require_command docker

if [[ -z "$IMAGE_TAG" ]]; then
  commit_hash="$(git -C "$repo_root" rev-parse --short=12 HEAD)"
  IMAGE_TAG="$(date +%Y%m%d)-${commit_hash}"
fi

image_ref="${IMAGE_REPOSITORY}:${IMAGE_TAG}"
image_os="${IMAGE_PLATFORM%%/*}"
image_architecture="${IMAGE_PLATFORM#*/}"

if [[ -z "$image_os" || -z "$image_architecture" || "$image_os" == "$image_architecture" ]]; then
  echo "Invalid platform: $IMAGE_PLATFORM. Expected OS/ARCH, for example linux/arm64." >&2
  exit 1
fi

if ! git -C "$repo_root" diff --quiet || ! git -C "$repo_root" diff --cached --quiet; then
  echo "Warning: worktree has uncommitted changes; image tag uses HEAD commit only." >&2
fi

if [[ "$RUN_TESTS" == "1" ]]; then
  echo "Running local tests..."
  (cd "$repo_root" && mvn test)
fi

echo "Building local Docker image: $image_ref ($IMAGE_PLATFORM)"
(cd "$repo_root" && mvn -DskipTests \
  -Ddocker.image.repository="$IMAGE_REPOSITORY" \
  -Dimage.tag="$IMAGE_TAG" \
  -Djib.from.platform.os="$image_os" \
  -Djib.from.platform.architecture="$image_architecture" \
  jib:dockerBuild)

docker image inspect "$image_ref" >/dev/null

if [[ "$PUSH_IMAGE" == "1" ]]; then
  echo "Pushing image: $image_ref"
  docker push "$image_ref"
fi

if [[ "$UPDATE_ENV" == "1" ]]; then
  env_file="$repo_root/deploy/.env"
  if [[ ! -f "$env_file" ]]; then
    cp "$repo_root/deploy/.env.example" "$env_file"
  fi

  tmp_file="$(mktemp)"
  awk -v image="$image_ref" '
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
  ' "$env_file" > "$tmp_file"
  mv "$tmp_file" "$env_file"
  chmod 600 "$env_file"
  echo "Updated deploy/.env with APP_IMAGE=$image_ref"
fi

echo "Image ready: $image_ref"
