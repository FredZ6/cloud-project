package com.cloud.inventory.service;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import com.cloud.inventory.repo.InventoryReleaseEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReleaseAuditServiceCursorTest {

    @Mock
    private InventoryReleaseEventRepository inventoryReleaseEventRepository;

    @InjectMocks
    private InventoryReleaseAuditService inventoryReleaseAuditService;

    @Test
    void listReleaseEventsCursorRejectsInvalidAfter() {
        assertThrows(
                ResponseStatusException.class,
                () -> inventoryReleaseAuditService.listReleaseEventsCursor(null, null, null, 20, "!!!")
        );
    }

    @Test
    void listReleaseEventsCursorUsesSizePlusOneAndStableSortAndComputesNextCursor() {
        int size = 2;
        Instant t = Instant.parse("2026-02-11T10:00:00Z");

        InventoryReleaseEventEntity e1 = new InventoryReleaseEventEntity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "PAYMENT_FAILED",
                t
        );
        InventoryReleaseEventEntity e2 = new InventoryReleaseEventEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "PAYMENT_FAILED",
                t
        );
        InventoryReleaseEventEntity extra = new InventoryReleaseEventEntity(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                "PAYMENT_FAILED",
                Instant.parse("2026-02-11T09:59:59Z")
        );

        when(inventoryReleaseEventRepository.findCursorPage(
                ArgumentMatchers.<Specification<InventoryReleaseEventEntity>>any(),
                any(Sort.class),
                eq(size + 1)
        ))
                .thenReturn(List.of(e1, e2, extra));

        var page = inventoryReleaseAuditService.listReleaseEventsCursor(null, null, null, size, null);

        assertEquals(size, page.items().size());
        assertTrue(page.hasMore());
        assertEquals(ReleaseEventsCursor.encode(e2.getCreatedAt(), e2.getId()), page.nextCursor());

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(inventoryReleaseEventRepository).findCursorPage(
                ArgumentMatchers.<Specification<InventoryReleaseEventEntity>>any(),
                sortCaptor.capture(),
                eq(size + 1)
        );

        Sort sort = sortCaptor.getValue();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
    }
}
