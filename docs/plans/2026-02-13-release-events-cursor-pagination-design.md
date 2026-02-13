# Release Events Cursor Pagination + Benchmark (Design)

**Date:** 2026-02-13

## Background

The inventory service exposes release-audit events via:
- Offset pagination: `GET /api/stocks/release-events?page&size&from&to&orderId`
- CSV export: `GET /api/stocks/release-events/export`

Offset pagination is easy for shallow pages but degrades for deep pages (large `OFFSET`), which is a common “resume-grade” performance topic. We will add a cursor/keyset pagination endpoint and produce a benchmark report that quantifies the improvement.

## Goals

- Add a new cursor-based API for release events without breaking the existing offset API.
- Use **stable ordering** (`created_at DESC, id DESC`) for deterministic paging.
- Provide a benchmark report that demonstrates:
  - Offset `page=0` vs offset deep page (configurable)
  - Cursor paging (sequential “infinite scroll”)
  - Index impact (EXPLAIN ANALYZE) before/after

## Non-goals

- Replace or remove the existing offset endpoint.
- Add cache for release-events queries.
- Add cryptographic signing of cursors (we’ll document that cursors must be used with the same filters).

## API Design

### New endpoint

`GET /api/stocks/release-events/cursor`

Query params:
- `orderId` (optional UUID)
- `from` / `to` (optional ISO-8601 timestamps)
- `size` (default `20`, range `1..100`)
- `after` (optional cursor string)

Response:
- `items`: list of release events (`releaseId`, `orderId`, `reservationId`, `reason`, `createdAt`)
- `nextCursor`: string or `null`
- `hasMore`: boolean

### Ordering and keyset predicate

Sort: `created_at DESC, id DESC`

When `after` is provided:
- `created_at < afterCreatedAt`
- OR (`created_at = afterCreatedAt` AND `id < afterId`)

### Cursor format

Cursor encodes the last item on the current page:
- `createdAtEpochMillis:id`
- Base64url encoded (no padding) for safe URL transport

Validation:
- If `after` is invalid/unparseable, return `400`.

## Data Model and Indexes

Current table:
- `inventory_release_events(id PK, order_id UNIQUE, reservation_id, reason, created_at)`
- Existing index: `idx_inventory_release_events_created_at(created_at)`

Add a composite index to support stable ordering and keyset paging:
- `idx_inventory_release_events_created_at_id_desc (created_at DESC, id DESC)`

## Benchmark and Report

### Dataset

Two-stage approach (cost/time efficient):
1. Dry-run: seed **50k** rows to validate scripts + measurements.
2. Final: seed **200k** rows for the resume-ready benchmark.

### k6

Add a new k6 script:
- `scripts/perf/k6-inventory-release-events.js`

Scenarios:
- Offset shallow: `page=0`
- Offset deep: `page=500` (or configurable)
- Cursor: sequential paging using `nextCursor`

Thresholds (baseline):
- `p(95) < 250ms`
- `http_req_failed < 1%`

### Evidence artifacts

Write report + raw evidence to `docs/reports/`:
- `release-events-pagination-benchmark.md`
- k6 summaries (`.json`)
- `EXPLAIN (ANALYZE, BUFFERS)` outputs for offset vs cursor, before vs after index

## Rollout

- Keep offset endpoint unchanged.
- Add cursor endpoint behind the same service/runtime.
- Update runbook/README with the new endpoint and benchmark instructions.

