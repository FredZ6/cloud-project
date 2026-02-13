# Release Events Cursor Pagination Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a cursor/keyset pagination endpoint for inventory release-audit events and publish a benchmark report comparing offset vs cursor performance (with index evidence).

**Architecture:** Keep the existing offset endpoint unchanged. Add a new `/cursor` endpoint that pages by `(created_at DESC, id DESC)` using a base64url cursor encoding `(createdAtMillis:id)`. Add a composite DB index to support keyset ordering and include k6 + EXPLAIN evidence in `docs/reports/`.

**Tech Stack:** Spring Boot (Web + Data JPA), PostgreSQL, k6, Docker Compose, Prometheus/Micrometer.

---

### Task 1: Add cursor DTOs and cursor codec

**Files:**
- Create: `services/inventory-service/src/main/java/com/cloud/inventory/api/InventoryReleaseEventCursorPageResponse.java`
- Create: `services/inventory-service/src/main/java/com/cloud/inventory/service/ReleaseEventsCursor.java`

**Step 1: Write the failing unit tests (cursor codec)**

Create:
- `services/inventory-service/src/test/java/com/cloud/inventory/service/ReleaseEventsCursorTest.java`

Test cases:
- encode/decode roundtrip
- invalid base64 -> throws `ResponseStatusException` mapping layer will translate to 400 (or throw a custom exception)
- invalid payload format -> 400

**Step 2: Run the test to verify it fails**

Run:
```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
mvn -B -ntp -pl services/inventory-service test -Dtest=ReleaseEventsCursorTest
```

Expected: FAIL (class missing).

**Step 3: Implement minimal cursor codec**

Implement:
- `ReleaseEventsCursor.encode(Instant createdAt, UUID id)`
- `ReleaseEventsCursor.decode(String after)` -> `{createdAt, id}`

**Step 4: Run the test to verify it passes**

Run the same command; expected PASS.

**Step 5: Commit**

```bash
git add services/inventory-service/src/main/java/com/cloud/inventory/service/ReleaseEventsCursor.java \
  services/inventory-service/src/test/java/com/cloud/inventory/service/ReleaseEventsCursorTest.java \
  services/inventory-service/src/main/java/com/cloud/inventory/api/InventoryReleaseEventCursorPageResponse.java
git commit -m "feat(inventory): add release-events cursor codec and response DTO"
```

---

### Task 2: Add cursor query in service layer

**Files:**
- Modify: `services/inventory-service/src/main/java/com/cloud/inventory/service/InventoryReleaseAuditService.java`

**Step 1: Write failing unit/integration test for cursor paging**

Create:
- `services/inventory-service/src/test/java/com/cloud/inventory/service/InventoryReleaseAuditServiceCursorTest.java`

Test cases (in-memory or slice test):
- without `after` returns newest first
- with `after` returns next page with no overlap
- stable ordering when `createdAt` ties exist (use same `createdAt` for multiple rows)

**Step 2: Run test, confirm FAIL**

Run:
```bash
mvn -B -ntp -pl services/inventory-service test -Dtest=InventoryReleaseAuditServiceCursorTest
```

**Step 3: Implement query**

Implementation notes:
- Reuse existing spec builder for `orderId/from/to`.
- Add keyset predicate when `after` is present.
- Use `Sort.by(desc(createdAt), desc(id))`.
- Fetch `size + 1` rows to compute `hasMore` and `nextCursor`.

**Step 4: Run test, confirm PASS**

**Step 5: Commit**

```bash
git add services/inventory-service/src/main/java/com/cloud/inventory/service/InventoryReleaseAuditService.java \
  services/inventory-service/src/test/java/com/cloud/inventory/service/InventoryReleaseAuditServiceCursorTest.java
git commit -m "feat(inventory): add keyset pagination query for release-events"
```

---

### Task 3: Add REST endpoint

**Files:**
- Modify: `services/inventory-service/src/main/java/com/cloud/inventory/api/StockController.java`

**Step 1: Add controller method**
- `GET /api/stocks/release-events/cursor`
- params: `orderId/from/to/size/after`
- return: `InventoryReleaseEventCursorPageResponse`

**Step 2: Add controller test**

Create:
- `services/inventory-service/src/test/java/com/cloud/inventory/api/StockControllerReleaseEventsCursorTest.java`

Test cases:
- invalid `after` -> 400
- returns `nextCursor` and `hasMore` when more rows exist

**Step 3: Run tests**

```bash
mvn -B -ntp -pl services/inventory-service test -Dtest=StockControllerReleaseEventsCursorTest
```

**Step 4: Commit**

```bash
git add services/inventory-service/src/main/java/com/cloud/inventory/api/StockController.java \
  services/inventory-service/src/test/java/com/cloud/inventory/api/StockControllerReleaseEventsCursorTest.java
git commit -m "feat(inventory): add release-events cursor endpoint"
```

---

### Task 4: Add DB index migration

**Files:**
- Create: `services/inventory-service/src/main/resources/db/migration/V4__add_release_events_cursor_index.sql`

**Step 1: Add composite index**
- `CREATE INDEX idx_inventory_release_events_created_at_id_desc ON inventory_release_events (created_at DESC, id DESC);`

**Step 2: Verify Flyway runs (local)**
- Start inventory-service once and confirm migration applied (or check schema via `psql`).

**Step 3: Commit**

```bash
git add services/inventory-service/src/main/resources/db/migration/V4__add_release_events_cursor_index.sql
git commit -m "db(inventory): add composite index for release-events cursor paging"
```

---

### Task 5: Add seed + explain helper scripts

**Files:**
- Create: `scripts/perf/seed-release-events.sh`
- Create: `scripts/perf/explain-release-events.sh`

**Step 1: Seed script**
- Uses `docker exec cloud-postgres psql` to:
  - `CREATE EXTENSION IF NOT EXISTS pgcrypto;`
  - `TRUNCATE inventory_release_events;`
  - `INSERT ... SELECT ... FROM generate_series(1, N)` using `gen_random_uuid()` and `date_trunc('second', now() - random()*interval '7 days')`

**Step 2: Explain script**
- Runs `EXPLAIN (ANALYZE, BUFFERS)` for:
  - offset `page=0`
  - offset deep page
  - cursor first page and subsequent page predicates (simulated via `after` values)
- Writes outputs to `docs/reports/`

**Step 3: Commit**

```bash
git add scripts/perf/seed-release-events.sh scripts/perf/explain-release-events.sh
git commit -m "perf: add seed and explain helpers for release-events pagination"
```

---

### Task 6: Add k6 benchmark script

**Files:**
- Create: `scripts/perf/k6-inventory-release-events.js`

**Step 1: Implement scenarios**
- Offset shallow (`page=0`)
- Offset deep (`page` via env, default 500)
- Cursor paging (keep `after` per VU; reset when `nextCursor` is null)

**Step 2: Run dry-run benchmark (50k)**

Run (example):
```bash
docker compose -f infra/docker-compose.yml up -d
./scripts/perf/seed-release-events.sh 50000
k6 run scripts/perf/k6-inventory-release-events.js
```

**Step 3: Commit**

```bash
git add scripts/perf/k6-inventory-release-events.js
git commit -m "perf: add k6 benchmark for release-events offset vs cursor"
```

---

### Task 7: Write the benchmark report and update docs

**Files:**
- Create: `docs/reports/release-events-pagination-benchmark.md`
- Modify: `docs/runbook.md`
- Modify: `README.md`

**Step 1: Dry-run report (50k)**
- Include k6 summary tables
- Include explain snippets (keep short; store full text files as evidence)

**Step 2: Final report (200k)**
- Re-run seed + explain + k6
- Update report with final numbers

**Step 3: Document new endpoint**
- Add cursor endpoint example curl to runbook/README

**Step 4: Commit**

```bash
git add docs/reports/release-events-pagination-benchmark.md docs/runbook.md README.md
git commit -m "docs: add release-events pagination benchmark report and cursor API usage"
```

---

### Task 8: Verify before completion

**Files:**
- Verify only

**Step 1: Local build**
```bash
mvn -B -ntp -DskipTests verify
```

**Step 2: CI validation**
- Push branch and confirm `quick-check`, `integration-tests`, `openapi-check` are green.

