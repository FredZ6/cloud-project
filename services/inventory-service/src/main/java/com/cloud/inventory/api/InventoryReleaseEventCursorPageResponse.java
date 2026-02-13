package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record InventoryReleaseEventCursorPageResponse(
        @Schema(description = "Release event page items")
        List<InventoryReleaseEventResponse> items,
        @Schema(description = "Page size", example = "20")
        int size,
        @Schema(description = "Whether more results exist after this page", example = "true")
        boolean hasMore,
        @Schema(description = "Cursor to pass as 'after' to fetch the next page; null when hasMore is false")
        String nextCursor
) {
}

