package com.cloud.inventory.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InventoryReleaseEventCursorPageResponseTest {

    @Test
    void recordHoldsValues() {
        var resp = new InventoryReleaseEventCursorPageResponse(List.of(), 20, false, null);

        assertEquals(20, resp.size());
        assertEquals(0, resp.items().size());
        assertNull(resp.nextCursor());
    }
}

