package com.cloud.inventory.api;

import com.cloud.inventory.domain.SkuStockEntity;
import com.cloud.inventory.service.InventoryReleaseAuditService;
import com.cloud.inventory.service.InventoryReservationService;
import com.cloud.inventory.service.ReservationOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Inventory", description = "Inventory and release-audit APIs")
public class StockController {

    private final InventoryReservationService reservationService;
    private final InventoryReleaseAuditService releaseAuditService;

    public StockController(InventoryReservationService reservationService, InventoryReleaseAuditService releaseAuditService) {
        this.reservationService = reservationService;
        this.releaseAuditService = releaseAuditService;
    }

    @PostMapping
    @Operation(summary = "Create or update SKU stock")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock upserted"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public StockResponse upsertStock(@Valid @RequestBody UpsertStockRequest request) {
        SkuStockEntity stock = reservationService.upsertStock(request.skuId(), request.availableQty());
        return toStockResponse(stock);
    }

    @GetMapping("/{skuId}")
    @Operation(summary = "Get stock by SKU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock found"),
            @ApiResponse(responseCode = "404", description = "Stock not found")
    })
    public StockResponse getStock(@PathVariable("skuId") String skuId) {
        SkuStockEntity stock = reservationService.getStock(skuId);
        return toStockResponse(stock);
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reserve stock for an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ReservationResponse reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        List<ReservationOutcome.ReservedItem> items = request.items().stream()
                .map(item -> new ReservationOutcome.ReservedItem(item.skuId(), item.quantity()))
                .toList();
        ReservationOutcome outcome = reservationService.reserveForOrder(request.orderId(), items);
        return toReservationResponse(outcome);
    }

    @GetMapping("/release-events")
    @Operation(summary = "Query inventory release audit events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit page returned"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters")
    })
    public InventoryReleaseEventPageResponse listReleaseEvents(
            @Parameter(description = "Filter by order ID")
            @RequestParam(value = "orderId", required = false) UUID orderId,
            @Parameter(description = "Filter from timestamp (ISO-8601)")
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Filter to timestamp (ISO-8601)")
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Page number, starting from 0")
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)")
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
    @Operation(summary = "Export inventory release audit events as CSV")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV generated"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters")
    })
    public ResponseEntity<String> exportReleaseEvents(
            @Parameter(description = "Filter by order ID")
            @RequestParam(value = "orderId", required = false) UUID orderId,
            @Parameter(description = "Filter from timestamp (ISO-8601)")
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Filter to timestamp (ISO-8601)")
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Maximum CSV rows (1-10000)")
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
