package com.cloud.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @Schema(description = "User identifier", example = "user-1")
        @NotBlank String userId,
        @Schema(description = "Order items")
        @NotEmpty List<@Valid OrderItemRequest> items
) {
    public record OrderItemRequest(
            @Schema(description = "SKU identifier", example = "SKU-001")
            @NotBlank String skuId,
            @Schema(description = "Requested quantity", example = "2")
            @Min(1) int quantity,
            @Schema(description = "Unit price", example = "19.90")
            @DecimalMin("0.01") BigDecimal price
    ) {
    }
}
