package com.cloud.inventory.cache;

import com.cloud.inventory.domain.SkuStockEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Service
public class InventoryStockCacheService {

    private static final Logger log = LoggerFactory.getLogger(InventoryStockCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheFallbackCounter;
    private final Counter cacheWriteCounter;
    private final Counter cacheEvictCounter;

    @Value("${app.cache.stock.enabled:true}")
    private boolean enabled;

    @Value("${app.cache.stock.key-prefix:inventory:stock:}")
    private String keyPrefix;

    @Value("${app.cache.stock.ttl-seconds:300}")
    private long ttlSeconds;

    public InventoryStockCacheService(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheHitCounter = meterRegistry.counter("inventory_stock_cache_hits");
        this.cacheMissCounter = meterRegistry.counter("inventory_stock_cache_misses");
        this.cacheFallbackCounter = meterRegistry.counter("inventory_stock_cache_fallback");
        this.cacheWriteCounter = meterRegistry.counter("inventory_stock_cache_writes");
        this.cacheEvictCounter = meterRegistry.counter("inventory_stock_cache_evictions");
    }

    public Optional<SkuStockEntity> get(String skuId) {
        if (!enabled) {
            return Optional.empty();
        }
        String key = key(skuId);
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                cacheMissCounter.increment();
                return Optional.empty();
            }
            CachedStock cached = objectMapper.readValue(payload, CachedStock.class);
            cacheHitCounter.increment();
            return Optional.of(new SkuStockEntity(
                    cached.skuId(),
                    cached.availableQty(),
                    cached.reservedQty(),
                    cached.updatedAt()
            ));
        } catch (JsonProcessingException exception) {
            cacheFallbackCounter.increment();
            log.warn("Inventory stock cache decode failed for key={}: {}", key, exception.getMessage());
            evict(skuId);
            return Optional.empty();
        } catch (RuntimeException exception) {
            cacheFallbackCounter.increment();
            log.warn("Inventory stock cache read failed for key={}: {}", key, exception.getMessage());
            return Optional.empty();
        }
    }

    public void put(SkuStockEntity stock) {
        if (!enabled || stock == null) {
            return;
        }
        try {
            CachedStock cached = new CachedStock(
                    stock.getSkuId(),
                    stock.getAvailableQty(),
                    stock.getReservedQty(),
                    stock.getUpdatedAt()
            );
            String payload = objectMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(
                    key(stock.getSkuId()),
                    payload,
                    Duration.ofSeconds(Math.max(1, ttlSeconds))
            );
            cacheWriteCounter.increment();
        } catch (JsonProcessingException exception) {
            cacheFallbackCounter.increment();
            log.warn("Inventory stock cache encode failed for sku={}: {}", stock.getSkuId(), exception.getMessage());
        } catch (RuntimeException exception) {
            cacheFallbackCounter.increment();
            log.warn("Inventory stock cache write failed for sku={}: {}", stock.getSkuId(), exception.getMessage());
        }
    }

    public void evict(String skuId) {
        if (!enabled || skuId == null || skuId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(key(skuId));
            cacheEvictCounter.increment();
        } catch (RuntimeException exception) {
            cacheFallbackCounter.increment();
            log.warn("Inventory stock cache evict failed for sku={}: {}", skuId, exception.getMessage());
        }
    }

    public void evictAll(Collection<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        skuIds.forEach(this::evict);
    }

    private String key(String skuId) {
        return keyPrefix + skuId;
    }

    private record CachedStock(String skuId, Integer availableQty, Integer reservedQty, Instant updatedAt) {
    }
}
