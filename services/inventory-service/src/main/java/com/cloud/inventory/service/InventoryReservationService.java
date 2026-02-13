package com.cloud.inventory.service;

import com.cloud.inventory.cache.InventoryStockCacheService;
import com.cloud.inventory.domain.InventoryReservationEntity;
import com.cloud.inventory.domain.InventoryReservationItemEntity;
import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import com.cloud.inventory.domain.ReservationStatus;
import com.cloud.inventory.domain.SkuStockEntity;
import com.cloud.inventory.repo.InventoryReleaseEventRepository;
import com.cloud.inventory.repo.InventoryReservationRepository;
import com.cloud.inventory.repo.SkuStockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryReservationService {

    private final SkuStockRepository skuStockRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReleaseEventRepository inventoryReleaseEventRepository;
    private final InventoryStockCacheService stockCacheService;

    public InventoryReservationService(SkuStockRepository skuStockRepository,
                                       InventoryReservationRepository reservationRepository,
                                       InventoryReleaseEventRepository inventoryReleaseEventRepository,
                                       InventoryStockCacheService stockCacheService) {
        this.skuStockRepository = skuStockRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryReleaseEventRepository = inventoryReleaseEventRepository;
        this.stockCacheService = stockCacheService;
    }

    @Transactional
    public SkuStockEntity upsertStock(String skuId, int availableQty) {
        String normalizedSkuId = normalizeSkuId(skuId);
        SkuStockEntity stock = skuStockRepository.findById(normalizedSkuId)
                .map(existing -> {
                    existing.setAvailableQty(availableQty);
                    return existing;
                })
                .orElseGet(() -> skuStockRepository.save(new SkuStockEntity(
                        normalizedSkuId,
                        availableQty,
                        0,
                        Instant.now()
                )));
        stockCacheService.put(stock);
        return stock;
    }

    @Transactional(readOnly = true)
    public SkuStockEntity getStock(String skuId) {
        String normalizedSkuId = normalizeSkuId(skuId);
        Optional<SkuStockEntity> cached = stockCacheService.get(normalizedSkuId);
        if (cached.isPresent()) {
            return cached.get();
        }

        SkuStockEntity stock = skuStockRepository.findById(normalizedSkuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for sku: " + normalizedSkuId));
        stockCacheService.put(stock);
        return stock;
    }

    @Transactional
    public ReservationOutcome reserveForOrder(UUID orderId, List<ReservationOutcome.ReservedItem> requestedItems) {
        return reservationRepository.findByOrderId(orderId)
                .map(this::toOutcome)
                .orElseGet(() -> reserveFresh(orderId, requestedItems));
    }

    @Transactional
    public Optional<InventoryReleaseOutcome> releaseReservationForOrder(UUID orderId, String reason) {
        InventoryReservationEntity reservation = reservationRepository.findByOrderId(orderId).orElse(null);
        if (reservation == null) {
            return Optional.empty();
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED || reservation.getStatus() == ReservationStatus.FAILED) {
            return Optional.empty();
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Unsupported reservation status for release: " + reservation.getStatus());
        }

        List<InventoryReleaseOutcome.ReleasedItem> releasedItems = reservation.getItems().stream()
                .map(item -> new InventoryReleaseOutcome.ReleasedItem(item.getSkuId(), item.getQuantity()))
                .toList();

        Set<String> skuIds = reservation.getItems().stream()
                .map(InventoryReservationItemEntity::getSkuId)
                .collect(Collectors.toSet());
        List<SkuStockEntity> lockedStocks = skuStockRepository.findAllBySkuIdInForUpdate(skuIds);
        Map<String, SkuStockEntity> stockBySku = toStockBySku(lockedStocks);

        for (InventoryReservationItemEntity item : reservation.getItems()) {
            SkuStockEntity stock = stockBySku.get(item.getSkuId());
            if (stock == null) {
                throw new IllegalStateException("Stock row missing for sku: " + item.getSkuId());
            }
            stock.release(item.getQuantity());
        }
        stockCacheService.evictAll(skuIds);

        Instant releasedAt = Instant.now();
        reservation.markReleased(reason);

        InventoryReleaseEventEntity releaseEvent = inventoryReleaseEventRepository.save(new InventoryReleaseEventEntity(
                UUID.randomUUID(),
                orderId,
                reservation.getId(),
                reason,
                releasedAt
        ));

        return Optional.of(new InventoryReleaseOutcome(
                releaseEvent.getId(),
                orderId,
                reservation.getId(),
                reason,
                releasedAt,
                releasedItems
        ));
    }

    private ReservationOutcome reserveFresh(UUID orderId, List<ReservationOutcome.ReservedItem> requestedItems) {
        if (requestedItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one item is required");
        }

        Map<String, Integer> requestedBySku = aggregateRequestedItems(requestedItems);
        Set<String> skuIds = requestedBySku.keySet();
        List<SkuStockEntity> lockedStocks = skuStockRepository.findAllBySkuIdInForUpdate(skuIds);

        if (lockedStocks.size() != skuIds.size()) {
            Set<String> found = lockedStocks.stream().map(SkuStockEntity::getSkuId).collect(Collectors.toSet());
            String missingSku = skuIds.stream().filter(sku -> !found.contains(sku)).findFirst().orElse("unknown");
            return saveFailedReservation(orderId, "SKU_NOT_FOUND:" + missingSku, requestedBySku);
        }

        for (SkuStockEntity stock : lockedStocks) {
            int requestedQty = requestedBySku.getOrDefault(stock.getSkuId(), 0);
            if (stock.getAvailableQty() < requestedQty) {
                String reason = "INSUFFICIENT_STOCK:" + stock.getSkuId()
                        + " available=" + stock.getAvailableQty()
                        + " requested=" + requestedQty;
                return saveFailedReservation(orderId, reason, requestedBySku);
            }
        }

        InventoryReservationEntity reservation = new InventoryReservationEntity(
                UUID.randomUUID(),
                orderId,
                ReservationStatus.RESERVED,
                null,
                Instant.now()
        );

        for (SkuStockEntity stock : lockedStocks) {
            int requestedQty = requestedBySku.get(stock.getSkuId());
            stock.reserve(requestedQty);
            reservation.addItem(new InventoryReservationItemEntity(
                    UUID.randomUUID(),
                    stock.getSkuId(),
                    requestedQty
            ));
        }

        InventoryReservationEntity saved = reservationRepository.save(reservation);
        stockCacheService.evictAll(skuIds);
        return toOutcome(saved);
    }

    private ReservationOutcome saveFailedReservation(UUID orderId, String reason, Map<String, Integer> requestedBySku) {
        InventoryReservationEntity failed = new InventoryReservationEntity(
                UUID.randomUUID(),
                orderId,
                ReservationStatus.FAILED,
                reason,
                Instant.now()
        );
        for (Map.Entry<String, Integer> entry : requestedBySku.entrySet()) {
            failed.addItem(new InventoryReservationItemEntity(
                    UUID.randomUUID(),
                    entry.getKey(),
                    entry.getValue()
            ));
        }
        return toOutcome(reservationRepository.save(failed));
    }

    private Map<String, Integer> aggregateRequestedItems(List<ReservationOutcome.ReservedItem> items) {
        Map<String, Integer> requestedBySku = new LinkedHashMap<>();
        for (ReservationOutcome.ReservedItem item : items) {
            String skuId = normalizeSkuId(item.skuId());
            if (item.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive for sku: " + skuId);
            }
            requestedBySku.merge(skuId, item.quantity(), Integer::sum);
        }
        return requestedBySku;
    }

    private Map<String, SkuStockEntity> toStockBySku(List<SkuStockEntity> stocks) {
        Map<String, SkuStockEntity> stockBySku = new LinkedHashMap<>();
        for (SkuStockEntity stock : stocks) {
            stockBySku.put(stock.getSkuId(), stock);
        }
        return stockBySku;
    }

    private String normalizeSkuId(String skuId) {
        if (skuId == null || skuId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skuId is required");
        }
        return skuId.trim();
    }

    public ReservationOutcome toOutcome(InventoryReservationEntity entity) {
        List<ReservationOutcome.ReservedItem> items = new ArrayList<>();
        for (InventoryReservationItemEntity item : entity.getItems()) {
            items.add(new ReservationOutcome.ReservedItem(item.getSkuId(), item.getQuantity()));
        }
        return new ReservationOutcome(
                entity.getId(),
                entity.getOrderId(),
                entity.getStatus(),
                entity.getReason(),
                entity.getCreatedAt(),
                items
        );
    }
}
