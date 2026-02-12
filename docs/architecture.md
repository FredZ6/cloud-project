# Architecture Overview

## Services

- All services expose:
  - OpenAPI JSON (`GET /v3/api-docs`) and Swagger UI (`GET /swagger-ui.html`).
  - Actuator health/info endpoints, including liveness/readiness probes.
- `order-service`
  - Owns orders and order lifecycle.
  - Provides idempotent order creation API.
  - Requires bearer token on `POST /api/orders`, validates token signature/issuer/subject, and enforces configured role (`buyer` by default).
  - Writes outbox events in the same DB transaction as order writes.
  - Triggers compensation by publishing `inventory.release.requested` on payment failure.
  - Adds identity context into event envelope (`identity.user_id`, `identity.roles`).
- `inventory-service`
  - Owns stock and reservations.
  - Exposes stock upsert/query/reserve APIs.
  - Uses Redis cache for hot stock reads (`GET /api/stocks/{skuId}`) with DB fallback.
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
  - Exposes JWKS (`GET /.well-known/jwks.json`) for signature validation.
  - Signs access tokens as RSA JWT (`RS256` + `kid`).
- `catalog-service`
  - Owns product metadata (SKU, name, description, price, active flag).
  - Exposes product upsert/query APIs (`PUT /api/catalog/products/{skuId}`, `GET /api/catalog/products`).
  - Provides a lightweight catalog baseline for order validation and read-side scenarios.
- `notification-service`
  - Subscribes to `payment.*` events via RabbitMQ topic binding.
  - Stores recent notification events in memory for read-side validation/demo.
  - Exposes notification query API (`GET /api/notifications/events`).

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
- `q.notification.payment-result` <- `payment.*`

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
- Cache invalidation in `inventory-service` on stock mutations (upsert/reserve/release).

## Observability baseline

- All six services expose Prometheus metrics (`/actuator/prometheus`).
- Cache hit/miss/fallback/eviction counters are emitted for stock-read cache behavior.
- HTTP requests include `X-Trace-Id` response header and MDC `trace_id` log field for correlation.
- Micrometer tracing exports OTLP spans from all services to local OTel Collector (`http://localhost:4318/v1/traces`).
- RabbitMQ template/listener observation is enabled for event publisher/consumer spans.
- Event messages include correlation headers (`x-trace-id`, `x-event-type`, `x-event-id`) for queue-level debugging.
- OTel Collector forwards traces to Jaeger for query/analysis.
- Grafana is provisioned with Prometheus + Jaeger datasources and default `Inventory Service Overview` + `Platform E2E Overview` dashboards.
- Prometheus alert rules cover cache fallback spikes, low cache hit ratio, elevated 5xx rate, and scrape-target down.
