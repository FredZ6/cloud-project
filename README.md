# cloud-order-platform

Event-driven e-commerce backend microservices based on Java + Spring Boot + RabbitMQ.

## Service split

Core domain services:
- `order-service`
- `inventory-service`
- `payment-service`

Platform support service:
- `auth-service`

`auth-service` is now added as a phase-2 evolution. It issues mock access tokens and supports token introspection. `POST /api/orders` now requires `Authorization: Bearer <token>`.

## Repository structure

```text
cloud-order-platform/
  docs/
  infra/
  services/
    order-service/
    inventory-service/
    payment-service/
    auth-service/
  .github/workflows/ci.yml
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker + Docker Compose

## Run infra

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
docker compose -f infra/docker-compose.yml up -d
```

Services:
- Postgres: `localhost:55432` (`cloud` / `cloud`)
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ management: <http://localhost:15672> (`cloud` / `cloud`)

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

## Quick API check (idempotent create order with mandatory bearer auth)

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

## Quick inventory check

Seed stock:

```bash
curl -i -X POST http://localhost:8082/api/stocks \
  -H 'Content-Type: application/json' \
  -d '{"skuId":"SKU-001","availableQty":10}'
```

Then create an order for `SKU-001`. `inventory-service` consumes `order.created`, creates a reservation, and updates stock.

Query release audit records (after a compensation flow):

```bash
curl -i "http://localhost:8082/api/stocks/release-events?page=0&size=20"
```

Export release audit records as CSV:

```bash
curl -L "http://localhost:8082/api/stocks/release-events/export?limit=1000" \
  -o inventory-release-events.csv
```

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

## Enforce branch protection (required checks)

Use the helper script below after the repository is pushed to GitHub and the target branch exists:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
GITHUB_TOKEN=<github-token-with-admin-permission> \
  ./scripts/enforce-branch-protection.sh <owner> <repo> main
```

What it enforces on `main`:
- Required status checks: `quick-check`, `integration-tests` (strict mode enabled)
- Required PR review count: `1`
- Dismiss stale reviews on new commits
- Require resolved conversations before merge
- Disable force push / branch deletion
- Enforce branch protection for admins

## Next milestones

1. Enforce branch protection with required checks (`quick-check`, `integration-tests`).
2. Move from shared-secret token verification to gateway/JWKS-based verification.
3. Add dashboard/report UI built on top of release audit query/export APIs.
