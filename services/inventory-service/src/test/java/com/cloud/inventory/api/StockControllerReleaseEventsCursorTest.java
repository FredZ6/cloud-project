package com.cloud.inventory.api;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import com.cloud.inventory.service.InventoryReleaseAuditService;
import com.cloud.inventory.service.InventoryReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StockController.class)
class StockControllerReleaseEventsCursorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryReservationService reservationService;

    @MockBean
    private InventoryReleaseAuditService releaseAuditService;

    @Test
    void invalidAfterReturns400() throws Exception {
        when(releaseAuditService.listReleaseEventsCursor(
                isNull(),
                isNull(),
                isNull(),
                eq(20),
                eq("!!!")
        ))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor"));

        mockMvc.perform(get("/api/stocks/release-events/cursor").param("after", "!!!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsHasMoreAndNextCursor() throws Exception {
        UUID releaseId = UUID.fromString("a32ee3e5-0f81-4a88-a361-f1f2158dfdbf");
        UUID orderId = UUID.fromString("4d8e1012-e736-4d43-b955-ed169d37efda");
        UUID reservationId = UUID.fromString("0fbdc2e2-7c24-4da1-ae4e-6a5b8ce4d7a2");

        InventoryReleaseEventEntity event = new InventoryReleaseEventEntity(
                releaseId,
                orderId,
                reservationId,
                "PAYMENT_FAILED",
                Instant.parse("2026-02-11T10:00:00Z")
        );

        when(releaseAuditService.listReleaseEventsCursor(isNull(), isNull(), isNull(), eq(20), isNull()))
                .thenReturn(new InventoryReleaseAuditService.ReleaseEventsCursorPage(
                        List.of(event),
                        true,
                        "next"
                ));

        mockMvc.perform(get("/api/stocks/release-events/cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value("next"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].releaseId").value(releaseId.toString()))
                .andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.items[0].reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.items[0].reason").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-02-11T10:00:00Z"));
    }
}

