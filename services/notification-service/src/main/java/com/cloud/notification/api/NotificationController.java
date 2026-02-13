package com.cloud.notification.api;

import com.cloud.notification.domain.NotificationEvent;
import com.cloud.notification.service.NotificationStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Validated
@Tag(name = "Notification", description = "Notification event APIs")
public class NotificationController {

    private final NotificationStoreService storeService;

    public NotificationController(NotificationStoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/events")
    @Operation(summary = "List recent notification events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events listed"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters")
    })
    public List<NotificationEventResponse> listEvents(
            @RequestParam(value = "orderId", required = false) UUID orderId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        return storeService.query(orderId, eventType, size).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/events/synthetic-failure")
    @Operation(summary = "Create a synthetic failure notification event for demos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Synthetic event created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public NotificationEventResponse createSyntheticFailure(@Valid @RequestBody SyntheticNotificationRequest request) {
        NotificationEvent event = storeService.appendSyntheticFailure(request.orderId(), request.paymentId(), request.reason());
        return toResponse(event);
    }

    private NotificationEventResponse toResponse(NotificationEvent event) {
        return new NotificationEventResponse(
                event.eventId(),
                event.eventType(),
                event.orderId(),
                event.paymentId(),
                event.reason(),
                event.traceId(),
                event.userId(),
                event.roles(),
                event.occurredAt(),
                event.receivedAt()
        );
    }
}
