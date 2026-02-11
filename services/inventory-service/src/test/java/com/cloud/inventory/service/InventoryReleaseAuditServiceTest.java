package com.cloud.inventory.service;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import com.cloud.inventory.repo.InventoryReleaseEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
class InventoryReleaseAuditServiceTest {

    @Mock
    private InventoryReleaseEventRepository inventoryReleaseEventRepository;

    @InjectMocks
    private InventoryReleaseAuditService inventoryReleaseAuditService;

    @Test
    void listReleaseEventsRejectsInvalidTimeRange() {
        Instant from = Instant.parse("2026-02-11T10:30:00Z");
        Instant to = Instant.parse("2026-02-11T10:00:00Z");

        assertThrows(
                ResponseStatusException.class,
                () -> inventoryReleaseAuditService.listReleaseEvents(null, from, to, 0, 20)
        );
    }

    @Test
    void listReleaseEventsDelegatesToRepositoryWithPaging() {
        UUID orderId = UUID.randomUUID();
        Instant from = Instant.parse("2026-02-11T09:00:00Z");
        Instant to = Instant.parse("2026-02-11T11:00:00Z");
        InventoryReleaseEventEntity sample = new InventoryReleaseEventEntity(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "PAYMENT_FAILED",
                Instant.parse("2026-02-11T10:00:00Z")
        );

        when(inventoryReleaseEventRepository.search(eq(orderId), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample)));

        var result = inventoryReleaseAuditService.listReleaseEvents(orderId, from, to, 1, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryReleaseEventRepository).search(eq(orderId), eq(from), eq(to), pageableCaptor.capture());

        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(5, pageableCaptor.getValue().getPageSize());
        assertEquals(1, result.getTotalElements());
        assertEquals(sample.getId(), result.getContent().get(0).getId());
    }

    @Test
    void exportReleaseEventsCsvDelegatesToRepositoryWithLimitAndEscapesCsv() {
        UUID orderId = UUID.randomUUID();
        Instant from = Instant.parse("2026-02-11T09:00:00Z");
        Instant to = Instant.parse("2026-02-11T11:00:00Z");
        InventoryReleaseEventEntity sample = new InventoryReleaseEventEntity(
                UUID.fromString("a32ee3e5-0f81-4a88-a361-f1f2158dfdbf"),
                orderId,
                UUID.fromString("4d8e1012-e736-4d43-b955-ed169d37efda"),
                "PAYMENT_FAILED,reason=\"hard\"",
                Instant.parse("2026-02-11T10:00:00Z")
        );

        when(inventoryReleaseEventRepository.search(eq(orderId), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample)));

        String csv = inventoryReleaseAuditService.exportReleaseEventsCsv(orderId, from, to, 50);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryReleaseEventRepository).search(eq(orderId), eq(from), eq(to), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
        assertTrue(csv.startsWith("release_id,order_id,reservation_id,reason,created_at\n"));
        assertTrue(csv.contains("\"PAYMENT_FAILED,reason=\"\"hard\"\"\""));
    }

    @Test
    void exportReleaseEventsCsvRejectsInvalidLimit() {
        assertThrows(
                ResponseStatusException.class,
                () -> inventoryReleaseAuditService.exportReleaseEventsCsv(null, null, null, 0)
        );
        assertThrows(
                ResponseStatusException.class,
                () -> inventoryReleaseAuditService.exportReleaseEventsCsv(null, null, null, 10001)
        );
    }
}
