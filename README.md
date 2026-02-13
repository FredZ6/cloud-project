# cloud-order-platform

Event-driven e-commerce backend microservices based on Java + Spring Boot + RabbitMQ.

## Service split

Core domain services:
- `catalog-service`
- `order-service`
- `inventory-service`
- `payment-service`

Platform support service:
- `auth-service`
- `notification-service`

`auth-service` issues RSA-signed JWT access tokens, exposes JWKS, and supports token introspection.
`notification-service` subscribes to `payment.*` events and exposes query APIs for recent notification events.
`POST /api/orders` requires `Authorization: Bearer <token>` and the configured role (`buyer` by default).

## Repository structure

```text
cloud-order-platform/
  docs/
    architecture.md
    event-contracts.md
    runbook.md
    deploy-aws.md
  infra/
    terraform/bootstrap-state/
    terraform/aws/
  services/
    order-service/
    inventory-service/
    payment-service/
    auth-service/
    catalog-service/
    notification-service/
  .github/workflows/ci.yml
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker + Docker Compose
- Terraform 1.6+ (for AWS IaC)

## Run infra

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
docker compose -f infra/docker-compose.yml up -d
```

Services:
- Postgres: `localhost:55432` (`cloud` / `cloud`)
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ management: <http://localhost:15672> (`cloud` / `cloud`)
- Redis: `localhost:6379`
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3000` (`admin` / `admin`)
- Jaeger: `http://localhost:16686`
- OTel Collector OTLP HTTP: `http://localhost:4318`

## Build once (recommended)

`spring-boot:run` runs `test-compile` first, and `order-service` integration tests depend on other service modules.
To avoid local dependency-resolution issues, build all modules once:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -B -ntp -DskipTests install
```

## Build service images (Milestone 3 phase 1)

Build one service image:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
./scripts/build-service-image.sh order-service
```

Build all service images:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
./scripts/build-all-images.sh
```

Default local image naming:
- `cloud-order-platform/<service>:local`
- Example: `cloud-order-platform/order-service:local`

## Run order-service

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -pl services/order-service spring-boot:run
```

API base: `http://localhost:8081`

## Run inventory-service

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -pl services/inventory-service spring-boot:run
```

API base: `http://localhost:8082`

Release audit dashboard:
- `http://localhost:8082/dashboard`
- `http://localhost:8082/release-dashboard.html`

## Run payment-service

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -pl services/payment-service spring-boot:run
```

API base: `http://localhost:8083`

## Run auth-service

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -pl services/auth-service spring-boot:run
```

API base: `http://localhost:8084`

JWKS endpoint: `http://localhost:8084/.well-known/jwks.json`

## Run catalog-service

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -pl services/catalog-service spring-boot:run
```

API base: `http://localhost:8085`

## Run notification-service

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -pl services/notification-service spring-boot:run
```

API base: `http://localhost:8086`

## API docs & health probes

OpenAPI endpoints:
- order-service: `http://localhost:8081/v3/api-docs`
- inventory-service: `http://localhost:8082/v3/api-docs`
- payment-service: `http://localhost:8083/v3/api-docs`
- auth-service: `http://localhost:8084/v3/api-docs`
- catalog-service: `http://localhost:8085/v3/api-docs`
- notification-service: `http://localhost:8086/v3/api-docs`

Swagger UI:
- order-service: `http://localhost:8081/swagger-ui.html`
- inventory-service: `http://localhost:8082/swagger-ui.html`
- payment-service: `http://localhost:8083/swagger-ui.html`
- auth-service: `http://localhost:8084/swagger-ui.html`
- catalog-service: `http://localhost:8085/swagger-ui.html`
- notification-service: `http://localhost:8086/swagger-ui.html`

Health endpoints (all services):
- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`

Prometheus endpoints:
- `http://localhost:8081/actuator/prometheus` (`order-service`)
- `http://localhost:8082/actuator/prometheus`
- `http://localhost:8083/actuator/prometheus` (`payment-service`)

Monitoring stack UIs:
- Prometheus targets: `http://localhost:9090/targets`
- Alert rules: `http://localhost:9090/alerts`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3000`
- Jaeger traces: `http://localhost:16686`

Operational guide: `docs/runbook.md`
AWS deployment foundation guide: `docs/deploy-aws.md`

OpenAPI check helper:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
./scripts/verify-openapi.sh
```

## Quick API check (idempotent create order with mandatory bearer auth + buyer role)

Issue a token from `auth-service`:

```bash
TOKEN=$(curl -s -X POST http://localhost:8084/api/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","roles":["buyer"]}' | jq -r '.accessToken')
```

Create order with bearer auth:

```bash
curl -i -X POST http://localhost:8081/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-key-001' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "userId": "user-1",
    "items": [
      {"skuId": "SKU-001", "quantity": 2, "price": 19.90},
      {"skuId": "SKU-002", "quantity": 1, "price": 8.50}
    ]
  }'
```

Repeat the same request with the same `Idempotency-Key` and it returns the same order with `reused=true`.

Role gate configuration (`order-service`):

- `AUTH_REQUIRED_ORDER_ROLE` (default: `buyer`)

## Quick inventory check

Seed stock:

```bash
curl -i -X POST http://localhost:8082/api/stocks \
  -H 'Content-Type: application/json' \
  -d '{"skuId":"SKU-001","availableQty":10}'
```

Then create an order for `SKU-001`. `inventory-service` consumes `order.created`, creates a reservation, and updates stock.

Read stock (hot-read path with Redis cache):

```bash
curl -i http://localhost:8082/api/stocks/SKU-001
```

Query release audit records (after a compensation flow):

```bash
curl -i "http://localhost:8082/api/stocks/release-events?page=0&size=20"
```

Query release audit records via cursor pagination (stable for deep paging):

```bash
# page 1
curl -i "http://localhost:8082/api/stocks/release-events/cursor?limit=20"

# page 2 (use nextCursor from the previous response)
curl -i "http://localhost:8082/api/stocks/release-events/cursor?limit=20&cursor=<nextCursor>"
```

Export release audit records as CSV:

```bash
curl -L "http://localhost:8082/api/stocks/release-events/export?limit=1000" \
  -o inventory-release-events.csv
```

Check cache/observability metrics:

```bash
curl -s http://localhost:8082/actuator/prometheus | rg 'inventory_stock_cache_|http_server_requests_seconds'
for port in 8081 8082 8083 8084 8085 8086; do
  curl -sf "http://localhost:${port}/actuator/prometheus" >/dev/null && echo "prometheus ok :${port}"
done
```

Trace propagation check:

```bash
TRACE_ID="demo-trace-001"
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8081/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8082/api/stocks/SKU-001 | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8083/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8084/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8085/actuator/health | rg 'X-Trace-Id'
curl -i -H "X-Trace-Id: ${TRACE_ID}" http://localhost:8086/actuator/health | rg 'X-Trace-Id'
```

Open Jaeger and query services `auth-service`, `catalog-service`, `order-service`, `inventory-service`, `payment-service`, and `notification-service`:

```bash
open http://localhost:16686
```

Queue-level correlation check (RabbitMQ message headers):

1. Open `http://localhost:15672`.
2. Inspect queues `q.inventory.order-created`, `q.payment.inventory-reserved`, `q.order.payment-result`.
3. Verify headers contain `x-trace-id` and `x-event-type` (and `traceparent` on outbox-published events).

## Quick catalog check

Upsert and read a product:

```bash
curl -i -X PUT http://localhost:8085/api/catalog/products/SKU-001 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo Product","description":"Sample item","price":19.90,"active":true}'

curl -i http://localhost:8085/api/catalog/products/SKU-001
```

Query notification events:

```bash
curl -i "http://localhost:8086/api/notifications/events?size=20"
```

## Performance quick check (k6)

Install [k6](https://k6.io/docs/get-started/installation/), then run:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
k6 run scripts/perf/k6-inventory-stock-read.js
```

Optional env overrides:

```bash
INVENTORY_BASE_URL=http://localhost:8082 SKU_ID=SKU-PERF-001 \
  k6 run scripts/perf/k6-inventory-stock-read.js
```

## Alert Rules (Prometheus)

- `InventoryCacheFallbackSpike`
- `InventoryHttp5xxHigh`
- `InventoryCacheHitRatioLow`
- `OrderHttp5xxHigh`
- `PaymentHttp5xxHigh`
- `AuthHttp5xxHigh`
- `CatalogHttp5xxHigh`
- `NotificationHttp5xxHigh`
- `ServiceScrapeDown`

Rules file:
- `infra/monitoring/prometheus/alerts.yml`

## Current event chain

1. `order-service` writes order + outbox and publishes `order.created`.
2. `inventory-service` consumes `order.created`, reserves stock, and publishes `inventory.reserved` / `inventory.failed`.
3. `payment-service` consumes `inventory.reserved` and publishes `payment.succeeded` / `payment.failed`.
4. `order-service` consumes `inventory.*` and `payment.*` and transitions status:
   `NEW -> RESERVED -> CONFIRMED` or `FAILED`.
5. On `payment.failed`, `order-service` publishes `inventory.release.requested`.
6. `inventory-service` consumes `inventory.release.requested` and compensates reservation:
   `RESERVED -> RELEASED` (stock is returned).
7. `inventory-service` persists and emits `inventory.released` for audit/reporting.
8. `identity` + `trace_id` are propagated through event envelopes for cross-service auditing.
9. `notification-service` consumes `payment.*` events for downstream notification/reporting use cases.

## Enforce branch protection (required checks)

Use the helper script below after the repository is pushed to GitHub and the target branch exists:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
GITHUB_TOKEN=<github-token-with-admin-permission> \
  ./scripts/enforce-branch-protection.sh <owner> <repo> main
```

What it enforces on `main`:
- Required status checks: `quick-check`, `integration-tests`, `openapi-check` (strict mode enabled)
- Required PR review count: `1`
- Dismiss stale reviews on new commits
- Require resolved conversations before merge
- Disable force push / branch deletion
- Enforce branch protection for admins

## Milestone status

- [x] Enforce branch protection with required checks (`quick-check`, `integration-tests`, `openapi-check`)
- [x] Move from shared-secret token verification to gateway/JWKS-based verification
- [x] Enforce RBAC for order creation (`buyer` role, configurable)
- [x] Add auth negative-path integration tests (`401` missing token, `403` missing role, `400` subject mismatch)
- [x] Add dashboard/report UI built on top of release audit query/export APIs
- [x] Add OpenAPI docs + liveness/readiness probes + runbook
- [x] Add `catalog-service` and `notification-service` baseline modules
- [x] Milestone 2 (phase 1): Redis stock-read cache + Prometheus metrics + k6 baseline script
- [x] Milestone 2 (phase 2): OTel collector + Jaeger + Grafana + Prometheus alert rules
- [x] Milestone 2 (phase 3): Extend tracing/metrics coverage to `order-service` and `payment-service`
- [x] Milestone 2 (phase 4): RabbitMQ publish/consume observation + message-level trace headers
- [x] Milestone 3 (phase 1): Service image build scripts + Terraform AWS foundation (VPC/public subnets/ECS cluster/ECR)
- [x] Milestone 3 (phase 2): Terraform ECS deploy layer (ALB path routing + task definitions + ECS services + IAM roles + CloudWatch logs)
- [x] Milestone 3 (phase 3): Secrets Manager/SSM injection hooks + GitHub Actions CD workflow for ECR/ECS rolling deploy
- [x] Milestone 3 (phase 4): Terraform remote state (S3 + DynamoDB lock) + CD terraform plan/apply deployment gate
