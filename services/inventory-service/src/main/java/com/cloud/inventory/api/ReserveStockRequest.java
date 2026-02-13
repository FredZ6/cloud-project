package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReserveStockRequest(
        @Schema(description = "Order ID")
        @NotNull UUID orderId,
        @Schema(description = "Items to reserve")
        @NotEmpty List<@Valid ReserveItem> items
) {
    public record ReserveItem(
            @Schema(description = "SKU identifier", example = "SKU-001")
            @NotBlank String skuId,
            @Schema(description = "Requested quantity", example = "1")
            @Min(1) int quantity
    ) {
    }
}
