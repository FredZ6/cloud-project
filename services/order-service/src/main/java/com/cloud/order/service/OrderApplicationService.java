package com.cloud.order.service;

import com.cloud.order.api.CreateOrderRequest;
import com.cloud.order.api.EventEnvelope;
import com.cloud.order.api.EventIdentity;
import com.cloud.order.api.OrderCreatedData;
import com.cloud.order.api.OrderCreatedItem;
import com.cloud.order.api.OrderResponse;
import com.cloud.order.auth.AuthTokenClaims;
import com.cloud.order.auth.AuthTokenVerifier;
import com.cloud.order.domain.IdempotencyKeyEntity;
import com.cloud.order.domain.OrderEntity;
import com.cloud.order.domain.OrderItemEntity;
import com.cloud.order.domain.OrderStatus;
import com.cloud.order.domain.OutboxEventEntity;
import com.cloud.order.domain.OutboxStatus;
import com.cloud.order.repo.IdempotencyKeyRepository;
import com.cloud.order.repo.OrderRepository;
import com.cloud.order.repo.OutboxEventRepository;
import com.cloud.order.tracing.TraceIdContextResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final AuthTokenVerifier authTokenVerifier;
    private final TraceIdContextResolver traceIdContextResolver;

    @Value("${app.auth.required-order-role:buyer}")
    private String requiredOrderRole;

    public OrderApplicationService(OrderRepository orderRepository,
                                   IdempotencyKeyRepository idempotencyKeyRepository,
                                   OutboxEventRepository outboxEventRepository,
                                   ObjectMapper objectMapper,
                                   AuthTokenVerifier authTokenVerifier,
                                   TraceIdContextResolver traceIdContextResolver) {
        this.orderRepository = orderRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.authTokenVerifier = authTokenVerifier;
        this.traceIdContextResolver = traceIdContextResolver;
    }

    @Transactional
    public OrderResponse createOrder(String idempotencyKey, String authorizationHeader, CreateOrderRequest request) {
        String normalizedKey = normalizeHeader(idempotencyKey);
        String normalizedRequestUserId = request.userId().trim();
        AuthTokenClaims tokenClaims = authTokenVerifier.verifyBearerAuthorization(authorizationHeader)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization Bearer token is required"));
        if (!tokenClaims.userId().equals(normalizedRequestUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId does not match token subject");
        }
        if (!hasRequiredRole(tokenClaims.roles())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing required role: " + requiredOrderRole.trim());
        }
        EventIdentity eventIdentity = new EventIdentity(
                normalizedRequestUserId,
                tokenClaims.roles()
        );

        Optional<IdempotencyKeyEntity> existing = idempotencyKeyRepository.findById(normalizedKey);
        if (existing.isPresent()) {
            return getOrder(existing.get().getOrderId(), true);
        }

        Instant now = Instant.now();
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(),
                normalizedRequestUserId,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                OrderStatus.NEW,
                now
        );

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.items()) {
            BigDecimal normalizedPrice = itemRequest.price().setScale(2, RoundingMode.HALF_UP);
            OrderItemEntity item = new OrderItemEntity(
                    UUID.randomUUID(),
                    itemRequest.skuId().trim(),
                    itemRequest.quantity(),
                    normalizedPrice
            );
            order.addItem(item);
            total = total.add(normalizedPrice.multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));

        orderRepository.save(order);

        idempotencyKeyRepository.save(new IdempotencyKeyEntity(
                normalizedKey,
                order.getId(),
                "COMPLETED",
                now,
                now.plus(1, ChronoUnit.DAYS)
        ));

        outboxEventRepository.save(new OutboxEventEntity(
                UUID.randomUUID(),
                "OrderCreated",
                "order.created",
                createOrderCreatedPayload(order, eventIdentity),
                OutboxStatus.PENDING,
                now,
                null,
                null
        ));

        return toOrderResponse(order, false);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, boolean reused) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        return toOrderResponse(order, reused);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return getOrder(orderId, false);
    }

    private String createOrderCreatedPayload(OrderEntity order, EventIdentity eventIdentity) {
        List<OrderCreatedItem> items = order.getItems().stream()
                .map(item -> new OrderCreatedItem(item.getSkuId(), item.getQuantity(), item.getPrice()))
                .toList();
        OrderCreatedData data = new OrderCreatedData(order.getId(), order.getUserId(), items, order.getTotalAmount());
        EventEnvelope<OrderCreatedData> envelope = EventEnvelope.of(
                "OrderCreated",
                data,
                traceIdContextResolver.resolveOrRandom(),
                eventIdentity
        );
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OrderCreated event", exception);
        }
    }

    private String normalizeHeader(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        return idempotencyKey.trim();
    }

    private boolean hasRequiredRole(List<String> roles) {
        String normalizedRequiredRole = requiredOrderRole == null ? "" : requiredOrderRole.trim();
        if (normalizedRequiredRole.isEmpty()) {
            return true;
        }
        return roles != null && roles.stream().anyMatch(role -> normalizedRequiredRole.equalsIgnoreCase(role));
    }

    private OrderResponse toOrderResponse(OrderEntity order, boolean reused) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(item.getSkuId(), item.getQuantity(), item.getPrice()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                reused,
                items
        );
    }
}
