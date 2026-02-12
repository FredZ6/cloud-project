#!/usr/bin/env bash

set -euo pipefail

services=(
  auth-service
  catalog-service
  order-service
  inventory-service
  payment-service
  notification-service
)

for service in "${services[@]}"; do
  ./scripts/build-service-image.sh "${service}"
done

echo "Built ${#services[@]} service images."
