# Release Events Pagination Benchmark (Offset vs Cursor)

This benchmark compares **offset pagination** (`page`/`size`) with **cursor pagination** (`cursor`/`limit`) for the inventory release audit stream stored in `inventory_release_events`.

## Scope

Endpoints under test:

- Offset: `GET /api/stocks/release-events?page=<n>&size=<n>`
- Cursor: `GET /api/stocks/release-events/cursor?limit=<n>&cursor=<opaque>`

Cursor ordering is **stable** by `createdAt DESC, id DESC`. The cursor is a base64url encoding of `createdAtMillis:id` (no signature). **Assumption:** a cursor must be reused with the *same filters/sort* that produced it.

## Dataset + Environment

- Local docker-compose Postgres (`infra/docker-compose.yml`)
- Index used for both approaches:
  - `idx_inventory_release_events_created_at_id_desc (created_at DESC, id DESC)`
- Page size / limit: `20`
- Seeded datasets:
  - **50k rows**, deep offset: `page=2000` (offset `40000`)
  - **200k rows**, deep offset: `page=5000` (offset `100000`)

## Results (k6)

Notes:
- All numbers are from `k6 ... --summary-export ...` JSON outputs in `docs/reports/`.
- `cursor_paging` scenario performs **two HTTP requests per iteration** (page-1 then page-2 using `nextCursor`). Reported `req/s` is per-request.

### 50k rows

| Approach | Scenario | req/s | avg (ms) | p95 (ms) | http failures |
| --- | --- | ---:| ---:| ---:| ---:|
| Offset | page=0 | 129.83 | 12.47 | 20.15 | 0.00% |
| Offset | page=2000 | 125.27 | 16.79 | 26.31 | 0.00% |
| Cursor | page=1+2 | 132.14 | 10.49 | 19.13 | 0.00% |

### 200k rows

| Approach | Scenario | req/s | avg (ms) | p95 (ms) | http failures |
| --- | --- | ---:| ---:| ---:| ---:|
| Offset | page=0 | 124.15 | 18.07 | 28.44 | 0.00% |
| Offset | page=5000 | 117.37 | 25.10 | 41.75 | 0.00% |
| Cursor | page=1+2 | 133.01 | 10.01 | 20.20 | 0.00% |

## EXPLAIN Highlights (Postgres)

The SQL planner uses the composite index in all cases, but **deep offset still scans `offset + limit` rows**.

| Dataset | Query | Execution Time | Rows scanned/returned | Buffers (shared hit) |
| --- | --- | ---:| ---:| ---:|
| 50k | offset page=2000 | 18.530 ms | `40020` scanned / `20` returned | 40188 |
| 50k | cursor page=2 | 0.158 ms | `20` returned (`20` removed-by-filter) | 42 |
| 200k | offset page=5000 | 65.476 ms | `100020` scanned / `20` returned | 100589 |
| 200k | cursor page=2 | 0.203 ms | `20` returned (`20` removed-by-filter) | 43 |

## Takeaways

- Offset pagination is fine for shallow pages, but **degrades with deeper pages** (scan grows with offset).
- Cursor pagination stays **near-constant time per page** (the predicate jumps to a position in the ordered index).
- Implementation note: cursor paging must avoid `Page<T>` in Spring Data (it triggers a `COUNT(*)`). The cursor endpoint uses a custom repository method to fetch `limit + 1` rows and compute `hasMore` without a count query.

## Evidence Files

EXPLAIN:
- `docs/reports/release-events-explain-offset-page0-50k.txt`
- `docs/reports/release-events-explain-offset-deep-page-50k-page2000.txt`
- `docs/reports/release-events-explain-cursor-page1-50k.txt`
- `docs/reports/release-events-explain-cursor-page2-50k.txt`
- `docs/reports/release-events-explain-offset-page0-200k.txt`
- `docs/reports/release-events-explain-offset-deep-page-200k-page5000.txt`
- `docs/reports/release-events-explain-cursor-page1-200k.txt`
- `docs/reports/release-events-explain-cursor-page2-200k.txt`

k6:
- `docs/reports/k6-release-events-offset-page0-50k-summary.json`
- `docs/reports/k6-release-events-offset-deep-50k-page2000-summary.json`
- `docs/reports/k6-release-events-cursor-50k-summary.json`
- `docs/reports/k6-release-events-offset-page0-200k-summary.json`
- `docs/reports/k6-release-events-offset-deep-200k-page5000-summary.json`
- `docs/reports/k6-release-events-cursor-200k-summary.json`

