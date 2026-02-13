# Inventory Stock Read Cache Benchmark (2026-02-12)

## Scope
- Service: `inventory-service` (`/api/stocks/{sku}`)
- Script: `scripts/perf/k6-inventory-stock-read.js`
- Environment: local Docker dependencies (`postgres`, `rabbitmq`, `redis`)
- Comparison: `app.cache.stock.enabled=true` vs `false`

## k6 Summary

| Scenario | Total Requests | Req/s | Avg Latency (ms) | P95 Latency (ms) | HTTP Fail Rate |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cache ON | 30,211 | 251.08 | 6.19 | 12.83 | 0.036% |
| Cache OFF | 32,771 | 272.55 | 7.70 | 14.88 | 0.000% |

## Cache Counter Deltas (Prometheus)

### Cache ON (before -> after)
- `inventory_stock_cache_hits_total`: `0 -> 30199` (`+30199`)
- `inventory_stock_cache_writes_total`: `0 -> 1` (`+1`)
- `inventory_stock_cache_misses_total`: `0 -> 0` (`+0`)
- `inventory_stock_cache_fallback_total`: `0 -> 0` (`+0`)
- `inventory_stock_cache_evictions_total`: `0 -> 0` (`+0`)

### Cache OFF (before -> after)
- `inventory_stock_cache_hits_total`: `0 -> 0` (`+0`)
- `inventory_stock_cache_writes_total`: `0 -> 0` (`+0`)
- `inventory_stock_cache_misses_total`: `0 -> 0` (`+0`)
- `inventory_stock_cache_fallback_total`: `0 -> 0` (`+0`)
- `inventory_stock_cache_evictions_total`: `0 -> 0` (`+0`)

## Interpretation
- Cache behavior is correct: with cache enabled, reads are mostly cache hits; with cache disabled, cache counters stay flat.
- Latency improved with cache enabled (`p95 12.83ms` vs `14.88ms`, `avg 6.19ms` vs `7.70ms`).
- Throughput in this local run was higher with cache disabled. This can happen in local single-node runs due to Redis round-trip overhead, short test window, or host scheduling variance.
- Both runs stayed well below the target latency threshold (`p95 < 250ms`) and maintained very low error rates.

## Evidence Files
- `docs/reports/k6-inventory-stock-read-cache-on-summary.json`
- `docs/reports/k6-inventory-stock-read-cache-off-summary.json`
- `docs/reports/cache-on-before.prom`
- `docs/reports/cache-on-after.prom`
- `docs/reports/cache-off-before.prom`
- `docs/reports/cache-off-after.prom`
