# Event Contracts (v1)

## Envelope

```json
{
  "event_id": "uuid",
  "event_type": "OrderCreated",
  "occurred_at": "2026-02-10T12:34:56Z",
  "trace_id": "uuid",
  "identity": {
    "user_id": "user-1",
    "roles": ["buyer"]
  },
  "data": {},
  "version": 1
}
```

## Envelope.identity

```json
{
  "user_id": "string",
  "roles": ["string"]
}
```

## OrderCreated.data

```json
{
  "order_id": "uuid",
  "user_id": "string",
  "items": [
    { "sku_id": "string", "quantity": 1, "price": 19.90 }
  ],
  "total_amount": 39.80
}
```

## InventoryReserved.data

```json
{
  "order_id": "uuid",
  "reservation_id": "uuid",
  "reserved_items": [
    { "sku_id": "string", "quantity": 1 }
  ]
}
```

## InventoryFailed.data

```json
{
  "order_id": "uuid",
  "reservation_id": "uuid | null",
  "reason": "string"
}
```

## InventoryReleaseRequested.data

```json
{
  "order_id": "uuid",
  "reason": "PAYMENT_FAILED"
}
```

## InventoryReleased.data

```json
{
  "release_id": "uuid",
  "order_id": "uuid",
  "reservation_id": "uuid",
  "reason": "PAYMENT_FAILED",
  "released_items": [
    { "sku_id": "string", "quantity": 1 }
  ]
}
```

## Release Audit Query API

`GET /api/stocks/release-events`

Query params:
- `orderId` (optional UUID)
- `from` / `to` (optional ISO-8601 datetime)
- `page` (default `0`)
- `size` (default `20`, max `100`)

## Release Audit Export API

`GET /api/stocks/release-events/export`

Query params:
- `orderId` (optional UUID)
- `from` / `to` (optional ISO-8601 datetime)
- `limit` (default `1000`, max `10000`)

Response:
- `text/csv` attachment (`inventory-release-events.csv`)

## PaymentSucceeded.data

```json
{
  "order_id": "uuid",
  "payment_id": "uuid"
}
```

## PaymentFailed.data

```json
{
  "order_id": "uuid",
  "payment_id": "uuid",
  "reason": "string"
}
```

## Auth API (Phase 2)

Issue token: `POST /api/auth/token`

Request:

```json
{
  "userId": "user-1",
  "roles": ["buyer"],
  "ttlSeconds": 3600
}
```

Introspect token: `POST /api/auth/introspect`

Request:

```json
{
  "token": "payload.signature"
}
```

## Order API Auth Requirement

`POST /api/orders` requires `Authorization: Bearer <payload.signature>`.
