package com.cloud.inventory.api;

import java.util.List;

public record InventoryReleaseEventPageResponse(
        List<InventoryReleaseEventResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
