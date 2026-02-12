package com.cloud.order.service;

import com.cloud.order.api.CreateOrderRequest;
import com.cloud.order.auth.AuthTokenClaims;
import com.cloud.order.auth.AuthTokenVerifier;
import com.cloud.order.repo.IdempotencyKeyRepository;
import com.cloud.order.repo.OrderRepository;
import com.cloud.order.repo.OutboxEventRepository;
import com.cloud.order.tracing.TraceIdContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderApplicationServiceRoleTest {

    private OrderRepository orderRepository;
    private IdempotencyKeyRepository idempotencyKeyRepository;
    private OutboxEventRepository outboxEventRepository;
    private AuthTokenVerifier authTokenVerifier;
    private TraceIdContextResolver traceIdContextResolver;
    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        idempotencyKeyRepository = mock(IdempotencyKeyRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        authTokenVerifier = mock(AuthTokenVerifier.class);
        traceIdContextResolver = mock(TraceIdContextResolver.class);
        service = new OrderApplicationService(
                orderRepository,
                idempotencyKeyRepository,
                outboxEventRepository,
                new ObjectMapper(),
                authTokenVerifier,
                traceIdContextResolver
        );
        ReflectionTestUtils.setField(service, "requiredOrderRole", "buyer");
    }

    @Test
    void shouldRejectOrderWhenRequiredRoleIsMissing() {
        CreateOrderRequest request = new CreateOrderRequest(
                "user-1",
                List.of(new CreateOrderRequest.OrderItemRequest("SKU-001", 1, new BigDecimal("19.90")))
        );
        when(authTokenVerifier.verifyBearerAuthorization("Bearer token")).thenReturn(Optional.of(
                new AuthTokenClaims("user-1", List.of("viewer"), Instant.now().plusSeconds(300))
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrder("idem-1", "Bearer token", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(orderRepository, idempotencyKeyRepository, outboxEventRepository);
    }
}
