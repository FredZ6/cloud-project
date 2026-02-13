#!/usr/bin/env bash
set -euo pipefail

COUNT="${1:-}"
if [[ -z "${COUNT}" ]]; then
  echo "Usage: $0 <row_count>" >&2
  exit 2
fi
if ! [[ "${COUNT}" =~ ^[0-9]+$ ]]; then
  echo "row_count must be a non-negative integer" >&2
  exit 2
fi

COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose.yml}"
DB="${INVENTORY_DB_NAME:-inventory_db}"
USER="${INVENTORY_DB_USER:-cloud}"

echo "Seeding ${COUNT} rows into ${DB}.inventory_release_events..."

docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U "${USER}" -d "${DB}" -v ON_ERROR_STOP=1 -v count="${COUNT}" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;

TRUNCATE TABLE inventory_release_events;

INSERT INTO inventory_release_events (id, order_id, reservation_id, reason, created_at)
SELECT
  gen_random_uuid(),
  gen_random_uuid(),
  gen_random_uuid(),
  CASE
    WHEN random() < 0.60 THEN 'PAYMENT_FAILED'
    WHEN random() < 0.80 THEN 'ORDER_CANCELLED'
    WHEN random() < 0.95 THEN 'INVENTORY_EXPIRED'
    ELSE 'UNKNOWN'
  END,
  date_trunc('second', now() - random() * interval '7 days')
FROM generate_series(1, :count);

ANALYZE inventory_release_events;

SELECT COUNT(*) AS row_count FROM inventory_release_events;
SQL

