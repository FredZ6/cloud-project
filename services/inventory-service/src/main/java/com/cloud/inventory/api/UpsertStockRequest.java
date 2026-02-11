package com.cloud.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpsertStockRequest(
        @NotBlank String skuId,
        @Min(0) int availableQty
) {
}
