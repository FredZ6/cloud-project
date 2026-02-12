# Runbook

## Scope

This runbook covers local operations for:
- `order-service` (`8081`)
- `inventory-service` (`8082`)
- `payment-service` (`8083`)
- `auth-service` (`8084`)
- `catalog-service` (`8085`)
- `notification-service` (`8086`)

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker Desktop (or Docker Engine + Compose)

## Start Infrastructure

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
docker compose -f infra/docker-compose.yml up -d
```

Verify:
- PostgreSQL: `localhost:55432`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ UI: `http://localhost:15672` (`cloud` / `cloud`)
- Redis: `localhost:6379`
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3000` (`admin` / `admin`)
- Jaeger: `http://localhost:16686`
- OTel Collector OTLP HTTP: `http://localhost:4318`

If queue declaration fails after prior experiments (RabbitMQ `PRECONDITION_FAILED`), reset infra state:

```bash
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d
```

## Start Services

Build once (recommended, avoids local module dependency resolution issues during `spring-boot:run`):

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -B -ntp -DskipTests install
```

Open 6 terminals and run:

```bash
mvn -pl services/auth-service spring-boot:run
mvn -pl services/order-service spring-boot:run
mvn -pl services/inventory-service spring-boot:run
mvn -pl services/payment-service spring-boot:run
mvn -pl services/catalog-service spring-boot:run
mvn -pl services/notification-service spring-boot:run
```

## Health/Readiness Checks

Each service exposes:
- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`

Prometheus metrics endpoints:
- `http://localhost:8081/actuator/prometheus` (`order-service`)
- `http://localhost:8082/actuator/prometheus` (`inventory-service`)
- `http://localhost:8083/actuator/prometheus` (`payment-service`)
- `http://localhost:8084/actuator/prometheus` (`auth-service`)
- `http://localhost:8085/actuator/prometheus` (`catalog-service`)
- `http://localhost:8086/actuator/prometheus` (`notification-service`)

Examples:

```bash
curl -s http://localhost:8081/actuator/health | jq
curl -s http://localhost:8082/actuator/health/readiness | jq
```

## API Docs (OpenAPI)

Each service exposes:
- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`

Examples:

```bash
open http://localhost:8081/swagger-ui.html
open http://localhost:8084/swagger-ui.html
open http://localhost:8085/swagger-ui.html
open http://localhost:8086/swagger-ui.html
```

## Main Smoke Test

1. Issue bearer token from `auth-service`:

```bash
TOKEN=$(curl -s -X POST http://localhost:8084/api/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","roles":["buyer"]}' | jq -r '.accessToken')
```

2. Seed stock:

```bash
curl -i -X POST http://localhost:8082/api/stocks \
  -H 'Content-Type: application/json' \
  -d '{"skuId":"SKU-001","availableQty":10}'
```

3. Create order:

```bash
curl -i -X POST http://localhost:8081/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: smoke-001' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{"userId":"user-1","items":[{"skuId":"SKU-001","quantity":1,"price":19.90}]}'
```

4. Verify release audit dashboard/API:

```bash
open http://localhost:8082/dashboard
curl -s "http://localhost:8082/api/stocks/release-events?page=0&size=20" | jq
```

5. Verify catalog baseline API:

```bash
curl -i -X PUT http://localhost:8085/api/catalog/products/SKU-001 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo Product","description":"Sample item","price":19.90,"active":true}'

curl -s http://localhost:8085/api/catalog/products/SKU-001 | jq
```

6. Verify notification query API:

```bash
curl -s "http://localhost:8086/api/notifications/events?size=20" | jq
```

7. Verify inventory cache/metrics:

```bash
curl -s http://localhost:8082/api/stocks/SKU-001 | jq
curl -s http://localhost:8082/actuator/prometheus | rg 'inventory_stock_cache_'
```

8. Verify trace correlation:

```bash
TRACE_ID="runbook-trace-001"
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8081/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8082/api/stocks/SKU-001 | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8083/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8084/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8085/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8086/actuator/health | rg 'X-Trace-Id'
open http://localhost:16686
```

9. Verify message-level trace headers:

```bash
open http://localhost:15672
```

Check queue messages for headers:
- `x-trace-id`
- `x-event-type`
- `x-event-id`
- `traceparent` (for outbox-published events)

## Performance Baseline (k6)

Run stock-read load baseline:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
k6 run scripts/perf/k6-inventory-stock-read.js
```

Optional:

```bash
INVENTORY_BASE_URL=http://localhost:8082 SKU_ID=SKU-PERF-001 \
  k6 run scripts/perf/k6-inventory-stock-read.js
```

## Observability Stack Checks

Prometheus:

```bash
open http://localhost:9090/targets
open http://localhost:9090/alerts
```

Alertmanager:

```bash
open http://localhost:9093
```

Grafana:

```bash
open http://localhost:3000
```

Expected:
- Data source `Prometheus` and `Jaeger` are pre-provisioned.
- Dashboard `Inventory Service Overview` is available under folder `Cloud Platform`.
- Dashboard `Platform E2E Overview` is available under folder `Cloud Platform`.

## Known Failure Checks

### 1) JWT/JWKS or role verification failure (order-service)

Symptoms:
- `POST /api/orders` returns `401/403`.

Checks:
- `AUTH_JWKS_URI` points to auth JWKS endpoint.
- `AUTH_EXPECTED_ISSUER` matches auth token issuer.
- Access token `roles` includes `buyer` (or configured role in `AUTH_REQUIRED_ORDER_ROLE`).
- `http://localhost:8084/.well-known/jwks.json` returns a key set.

### 2) RabbitMQ backlog or DLQ growth

Symptoms:
- Orders stay in `NEW` or do not progress.

Checks in RabbitMQ UI:
- Main queues: `q.inventory.order-created`, `q.payment.inventory-reserved`, `q.order.payment-result`
- Retry queues: `*.retry`
- Dead letter queues: `*.dlq`

Action:
- Inspect message headers (`x-retry-count`, `x-dlq-reason`) and fix root cause before requeueing.

### 3) Database migration/startup failures

Symptoms:
- Service fails during startup with Flyway/JPA errors.

Checks:
- DB is up and reachable at `localhost:55432`.
- Service DB URLs/users/passwords are correct.
- Migrations in `src/main/resources/db/migration` are applied in order.

## Test Command

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -B -ntp test
```
