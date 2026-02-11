package com.cloud.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReserveStockRequest(
        @NotNull UUID orderId,
        @NotEmpty List<@Valid ReserveItem> items
) {
    public record ReserveItem(
            @NotBlank String skuId,
            @Min(1) int quantity
    ) {
    }
}
