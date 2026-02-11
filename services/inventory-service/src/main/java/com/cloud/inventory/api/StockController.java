package com.cloud.inventory.api;

import com.cloud.inventory.domain.SkuStockEntity;
import com.cloud.inventory.service.InventoryReleaseAuditService;
import com.cloud.inventory.service.InventoryReservationService;
import com.cloud.inventory.service.ReservationOutcome;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stocks")
@Validated
public class StockController {

    private final InventoryReservationService reservationService;
    private final InventoryReleaseAuditService releaseAuditService;

    public StockController(InventoryReservationService reservationService, InventoryReleaseAuditService releaseAuditService) {
        this.reservationService = reservationService;
        this.releaseAuditService = releaseAuditService;
    }

    @PostMapping
    public StockResponse upsertStock(@Valid @RequestBody UpsertStockRequest request) {
        SkuStockEntity stock = reservationService.upsertStock(request.skuId(), request.availableQty());
        return toStockResponse(stock);
    }

    @GetMapping("/{skuId}")
    public StockResponse getStock(@PathVariable("skuId") String skuId) {
        SkuStockEntity stock = reservationService.getStock(skuId);
        return toStockResponse(stock);
    }

    @PostMapping("/reservations")
    public ReservationResponse reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        List<ReservationOutcome.ReservedItem> items = request.items().stream()
                .map(item -> new ReservationOutcome.ReservedItem(item.skuId(), item.quantity()))
                .toList();
        ReservationOutcome outcome = reservationService.reserveForOrder(request.orderId(), items);
        return toReservationResponse(outcome);
    }

    @GetMapping("/release-events")
    public InventoryReleaseEventPageResponse listReleaseEvents(
            @RequestParam(value = "orderId", required = false) UUID orderId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        var result = releaseAuditService.listReleaseEvents(orderId, from, to, page, size);
        List<InventoryReleaseEventResponse> items = result.getContent().stream()
                .map(event -> new InventoryReleaseEventResponse(
                        event.getId(),
                        event.getOrderId(),
                        event.getReservationId(),
                        event.getReason(),
                        event.getCreatedAt()
                ))
                .toList();

        return new InventoryReleaseEventPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @GetMapping(value = "/release-events/export", produces = "text/csv")
    public ResponseEntity<String> exportReleaseEvents(
            @RequestParam(value = "orderId", required = false) UUID orderId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "limit", defaultValue = "1000") @Min(1) @Max(10000) int limit
    ) {
        String csv = releaseAuditService.exportReleaseEventsCsv(orderId, from, to, limit);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory-release-events.csv\"")
                .body(csv);
    }

    private StockResponse toStockResponse(SkuStockEntity stock) {
        return new StockResponse(
                stock.getSkuId(),
                stock.getAvailableQty(),
                stock.getReservedQty(),
                stock.getUpdatedAt()
        );
    }

    private ReservationResponse toReservationResponse(ReservationOutcome outcome) {
        List<ReservationResponse.ReservationItemResponse> items = outcome.items().stream()
                .map(item -> new ReservationResponse.ReservationItemResponse(item.skuId(), item.quantity()))
                .toList();
        return new ReservationResponse(
                outcome.reservationId(),
                outcome.orderId(),
                outcome.status().name(),
                outcome.reason(),
                outcome.createdAt(),
                items
        );
    }
}
