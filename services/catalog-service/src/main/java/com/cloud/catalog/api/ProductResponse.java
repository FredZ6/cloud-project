package com.cloud.catalog.api;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String skuId,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        Instant updatedAt
) {
}
