package com.cloud.inventory.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReleaseOutcome(
        UUID releaseId,
        UUID orderId,
        UUID reservationId,
        String reason,
        Instant releasedAt,
        List<ReleasedItem> items
) {
    public record ReleasedItem(String skuId, int quantity) {
    }
}
