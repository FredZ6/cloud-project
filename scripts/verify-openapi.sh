#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

services=(
  order-service
  inventory-service
  payment-service
  auth-service
  catalog-service
  notification-service
)

for service in "${services[@]}"; do
  pom="services/${service}/pom.xml"
  app_yml="services/${service}/src/main/resources/application.yml"

  rg -q "springdoc-openapi-starter-webmvc-ui" "${pom}" \
    || { echo "Missing springdoc dependency in ${pom}" >&2; exit 1; }
  rg -q "path:\\s*/v3/api-docs" "${app_yml}" \
    || { echo "Missing /v3/api-docs path in ${app_yml}" >&2; exit 1; }
  rg -q "path:\\s*/swagger-ui.html" "${app_yml}" \
    || { echo "Missing /swagger-ui.html path in ${app_yml}" >&2; exit 1; }
done

command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 1; }

if [[ "${OPENAPI_RUNTIME_CHECK:-1}" != "1" ]]; then
  echo "OpenAPI static checks passed (runtime check skipped)."
  exit 0
fi

AUTH_PORT="${AUTH_PORT:-18084}"
AUTH_LOG="/tmp/auth-openapi.log"
AUTH_PID=""

cleanup() {
  if [[ -n "${AUTH_PID}" ]] && kill -0 "${AUTH_PID}" >/dev/null 2>&1; then
    kill "${AUTH_PID}" >/dev/null 2>&1 || true
    wait "${AUTH_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

mvn -B -ntp -pl services/auth-service \
  spring-boot:run \
  "-Dspring-boot.run.arguments=--server.port=${AUTH_PORT}" \
  > "${AUTH_LOG}" 2>&1 &
AUTH_PID=$!

for _ in $(seq 1 90); do
  if curl -fsS "http://localhost:${AUTH_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -fsS "http://localhost:${AUTH_PORT}/actuator/health" >/dev/null 2>&1; then
  echo "auth-service did not become healthy for OpenAPI checks." >&2
  tail -n 200 "${AUTH_LOG}" >&2 || true
  exit 1
fi

curl -fsS "http://localhost:${AUTH_PORT}/v3/api-docs" \
  | jq -e '.openapi and .paths and .paths["/api/auth/token"] and .paths["/api/auth/introspect"]' \
  >/dev/null

curl -fsS "http://localhost:${AUTH_PORT}/swagger-ui.html" >/dev/null

echo "OpenAPI checks passed."
