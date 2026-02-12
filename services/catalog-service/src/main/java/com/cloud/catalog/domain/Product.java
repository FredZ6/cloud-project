package com.cloud.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Product(
        String skuId,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        Instant updatedAt
) {
}
