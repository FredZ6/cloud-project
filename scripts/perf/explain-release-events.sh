#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose.yml}"
DB="${INVENTORY_DB_NAME:-inventory_db}"
USER="${INVENTORY_DB_USER:-cloud}"

SIZE="${SIZE:-20}"
DEEP_PAGE="${DEEP_PAGE:-500}"

if ! [[ "${SIZE}" =~ ^[0-9]+$ ]] || [[ "${SIZE}" -lt 1 ]] || [[ "${SIZE}" -gt 1000 ]]; then
  echo "SIZE must be an integer between 1 and 1000" >&2
  exit 2
fi
if ! [[ "${DEEP_PAGE}" =~ ^[0-9]+$ ]]; then
  echo "DEEP_PAGE must be a non-negative integer" >&2
  exit 2
fi

DEEP_OFFSET="$((DEEP_PAGE * SIZE))"
REPORT_DIR="docs/reports"
mkdir -p "${REPORT_DIR}"

echo "Writing EXPLAIN (ANALYZE, BUFFERS) evidence into ${REPORT_DIR}/ ..."

after_line="$(
  docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U "${USER}" -d "${DB}" -t -A -F '|' -v ON_ERROR_STOP=1 \
    -c "SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS created_at_utc, id FROM inventory_release_events ORDER BY created_at DESC, id DESC OFFSET $((SIZE - 1)) LIMIT 1;"
)"

after_created_at="$(echo "${after_line}" | cut -d'|' -f1)"
after_id="$(echo "${after_line}" | cut -d'|' -f2)"

if [[ -z "${after_created_at}" || -z "${after_id}" ]]; then
  echo "No rows found in inventory_release_events. Seed first: ./scripts/perf/seed-release-events.sh <N>" >&2
  exit 1
fi

echo "Using page-size=${SIZE}, deep-page=${DEEP_PAGE} (offset=${DEEP_OFFSET})"
echo "Cursor for page-2 predicate uses after: created_at=${after_created_at}, id=${after_id}"

docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U "${USER}" -d "${DB}" -v ON_ERROR_STOP=1 <<SQL > "${REPORT_DIR}/release-events-explain-offset-page0.txt"
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, order_id, reservation_id, reason, created_at
FROM inventory_release_events
ORDER BY created_at DESC, id DESC
LIMIT ${SIZE} OFFSET 0;
SQL

docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U "${USER}" -d "${DB}" -v ON_ERROR_STOP=1 <<SQL > "${REPORT_DIR}/release-events-explain-offset-deep-page.txt"
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, order_id, reservation_id, reason, created_at
FROM inventory_release_events
ORDER BY created_at DESC, id DESC
LIMIT ${SIZE} OFFSET ${DEEP_OFFSET};
SQL

docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U "${USER}" -d "${DB}" -v ON_ERROR_STOP=1 <<SQL > "${REPORT_DIR}/release-events-explain-cursor-page1.txt"
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, order_id, reservation_id, reason, created_at
FROM inventory_release_events
ORDER BY created_at DESC, id DESC
LIMIT ${SIZE};
SQL

docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U "${USER}" -d "${DB}" -v ON_ERROR_STOP=1 <<SQL > "${REPORT_DIR}/release-events-explain-cursor-page2.txt"
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, order_id, reservation_id, reason, created_at
FROM inventory_release_events
WHERE
  created_at < '${after_created_at}'::timestamptz
  OR (created_at = '${after_created_at}'::timestamptz AND id < '${after_id}'::uuid)
ORDER BY created_at DESC, id DESC
LIMIT ${SIZE};
SQL

echo "Done."

