# Architecture Overview

## Services

- `order-service`
  - Owns orders and order lifecycle.
  - Provides idempotent order creation API.
  - Requires bearer token on `POST /api/orders` and validates token signature/subject.
  - Writes outbox events in the same DB transaction as order writes.
  - Triggers compensation by publishing `inventory.release.requested` on payment failure.
  - Adds identity context into event envelope (`identity.user_id`, `identity.roles`).
- `inventory-service`
  - Owns stock and reservations.
  - Exposes stock upsert/query/reserve APIs.
  - Exposes release-audit query API (`GET /api/stocks/release-events` with pagination/filtering).
  - Exposes release-audit CSV export API (`GET /api/stocks/release-events/export`).
  - Hosts release-audit dashboard UI (`GET /dashboard`).
  - Consumes `order.created` and publishes `inventory.reserved` / `inventory.failed`.
  - Consumes `inventory.release.requested` and compensates reserved stock (`RESERVED -> RELEASED`).
  - Persists `inventory_release_events` and emits `inventory.released` for audit.
  - Preserves incoming event `trace_id` and `identity` when publishing downstream events.
  - Uses consumer idempotency table (`consumed_messages`).
  - Uses retry queue + DLQ for failed consumption.
- `payment-service`
  - Consumes `inventory.reserved`.
  - Produces `payment.succeeded` / `payment.failed` with configurable mock mode.
  - Preserves incoming event `trace_id` and `identity` when publishing payment results.
  - Uses consumer idempotency table (`consumed_messages`).
  - Uses retry queue + DLQ for failed consumption.
- `auth-service`
  - Issues mock bearer tokens (`POST /api/auth/token`).
  - Supports token introspection (`POST /api/auth/introspect`).
  - Uses HMAC-based token signature for local verification in `order-service`.

## Messaging topology

- Exchange: `ecom.events` (topic)
- Routing keys:
  - `order.created`
  - `inventory.release.requested`
  - `inventory.released`
  - `inventory.reserved`
  - `inventory.failed`
  - `payment.succeeded`
  - `payment.failed`

Phase 1 binding:
- `q.inventory.order-created` <- `order.created`
- `q.inventory.order-created.retry` for delayed retry (TTL dead-letter back to `order.created`)
- `q.inventory.order-created.dlq` for exhausted/poison messages
- `q.inventory.release-requested` <- `inventory.release.requested`
- `q.inventory.release-requested.retry` for delayed retry (TTL dead-letter back to `inventory.release.requested`)
- `q.inventory.release-requested.dlq` for exhausted/poison messages
- `q.payment.inventory-reserved` <- `inventory.reserved`
- `q.payment.inventory-reserved.retry` for delayed retry (TTL dead-letter back to `inventory.reserved`)
- `q.payment.inventory-reserved.dlq` for exhausted/poison messages
- `q.order.inventory-result` <- `inventory.*`
- `q.order.payment-result` <- `payment.*`

## Reliability patterns

- API idempotency for `POST /orders` via `Idempotency-Key`.
- Outbox pattern in `order-service` to prevent lost events.
- Saga-style compensation: payment failure triggers inventory release via asynchronous event.
- Identity propagation in event envelope (`identity` + stable `trace_id`).
- Explicit inventory release audit record persistence (`inventory_release_events`).
- Consumer idempotency in `inventory-service` (`message_id` + `consumer` uniqueness).
- Retry/DLQ in `inventory-service` for transient and poison-message handling.
- Retry/DLQ in `payment-service` for transient and poison-message handling.
- Consumer idempotency in `payment-service` and `order-service` for result events.
