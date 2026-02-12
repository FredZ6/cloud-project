package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpsertStockRequest(
        @Schema(description = "SKU identifier", example = "SKU-001")
        @NotBlank String skuId,
        @Schema(description = "Available quantity", example = "100")
        @Min(0) int availableQty
) {
}
