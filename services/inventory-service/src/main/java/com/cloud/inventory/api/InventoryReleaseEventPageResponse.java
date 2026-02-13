package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record InventoryReleaseEventPageResponse(
        @Schema(description = "Release event page items")
        List<InventoryReleaseEventResponse> items,
        @Schema(description = "Current page index", example = "0")
        int page,
        @Schema(description = "Page size", example = "20")
        int size,
        @Schema(description = "Total matched event count", example = "37")
        long totalElements,
        @Schema(description = "Total pages", example = "2")
        int totalPages,
        @Schema(description = "Whether next page exists", example = "true")
        boolean hasNext
) {
}
