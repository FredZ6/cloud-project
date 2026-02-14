#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/build-service-image.sh <service-name> [image-tag]

Examples:
  ./scripts/build-service-image.sh order-service
  ./scripts/build-service-image.sh inventory cloud-order-platform/inventory-service:dev

Environment:
  DOCKER_PLATFORM  Optional Docker build platform (default: linux/amd64).
EOF
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
  exit 1
fi

raw_service="$1"

case "${raw_service}" in
  order|order-service) module="order-service" ;;
  inventory|inventory-service) module="inventory-service" ;;
  payment|payment-service) module="payment-service" ;;
  auth|auth-service) module="auth-service" ;;
  catalog|catalog-service) module="catalog-service" ;;
  notification|notification-service) module="notification-service" ;;
  *)
    echo "Unsupported service: ${raw_service}" >&2
    usage
    exit 1
    ;;
esac

image="${2:-cloud-order-platform/${module}:local}"
platform="${DOCKER_PLATFORM:-linux/amd64}"

echo "Building module ${module} -> ${image}"
docker build \
  --platform "${platform}" \
  --build-arg SERVICE_MODULE="${module}" \
  -t "${image}" \
  -f Dockerfile \
  .
