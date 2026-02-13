package com.cloud.order.api;

import com.cloud.order.service.OrderApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Validated
@Tag(name = "Orders", description = "Order creation and query APIs")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @PostMapping
    @Operation(
            summary = "Create order",
            description = "Creates an order with idempotency key, bearer token validation, and buyer-role authorization."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "200", description = "Idempotent key reused existing order"),
            @ApiResponse(responseCode = "400", description = "Invalid request or token subject mismatch"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Bearer token does not include required role")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Client idempotency key", required = true)
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Parameter(description = "Bearer token: Bearer <jwt>", required = true)
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderApplicationService.createOrder(idempotencyKey, authorization, request);
        HttpStatus status = response.reused() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public OrderResponse getOrder(@PathVariable("orderId") UUID orderId) {
        return orderApplicationService.getOrder(orderId);
    }
}
