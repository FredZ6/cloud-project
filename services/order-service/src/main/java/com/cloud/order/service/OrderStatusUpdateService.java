package com.cloud.order.service;

import com.cloud.order.domain.ConsumedMessageEntity;
import com.cloud.order.domain.OrderEntity;
import com.cloud.order.domain.OrderStatus;
import com.cloud.order.domain.OutboxEventEntity;
import com.cloud.order.domain.OutboxStatus;
import com.cloud.order.messaging.InventoryReleaseRequestedData;
import com.cloud.order.repo.ConsumedMessageRepository;
import com.cloud.order.repo.OrderRepository;
import com.cloud.order.repo.OutboxEventRepository;
import com.cloud.order.api.EventEnvelope;
import com.cloud.order.api.EventIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderStatusUpdateService {

    private final OrderRepository orderRepository;
    private final ConsumedMessageRepository consumedMessageRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderStatusUpdateService(OrderRepository orderRepository,
                                    ConsumedMessageRepository consumedMessageRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.consumedMessageRepository = consumedMessageRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void markReserved(String messageId, UUID orderId) {
        String consumer = "order.inventory-result";
        if (consumedMessageRepository.existsByMessageIdAndConsumer(messageId, consumer)) {
            return;
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.NEW) {
            order.setStatus(OrderStatus.RESERVED);
        }

        markConsumed(messageId, consumer);
    }

    @Transactional
    public void markInventoryFailed(String messageId, UUID orderId) {
        String consumer = "order.inventory-result";
        if (consumedMessageRepository.existsByMessageIdAndConsumer(messageId, consumer)) {
            return;
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() != OrderStatus.CONFIRMED) {
            order.setStatus(OrderStatus.FAILED);
        }

        markConsumed(messageId, consumer);
    }

    @Transactional
    public void markInventoryReleased(String messageId, UUID orderId) {
        String consumer = "order.inventory-result";
        if (consumedMessageRepository.existsByMessageIdAndConsumer(messageId, consumer)) {
            return;
        }

        // Inventory release is a compensation audit signal; order state is already terminal.
        markConsumed(messageId, consumer);
    }

    @Transactional
    public void markPaymentSucceeded(String messageId, UUID orderId) {
        String consumer = "order.payment-result";
        if (consumedMessageRepository.existsByMessageIdAndConsumer(messageId, consumer)) {
            return;
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() != OrderStatus.FAILED) {
            order.setStatus(OrderStatus.CONFIRMED);
        }

        markConsumed(messageId, consumer);
    }

    @Transactional
    public void markPaymentFailed(String messageId, UUID orderId, UUID traceId, EventIdentity identity) {
        String consumer = "order.payment-result";
        if (consumedMessageRepository.existsByMessageIdAndConsumer(messageId, consumer)) {
            return;
        }

        Instant now = Instant.now();
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.FAILED) {
            order.setStatus(OrderStatus.FAILED);
            outboxEventRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(),
                    "InventoryReleaseRequested",
                    "inventory.release.requested",
                    createInventoryReleaseRequestedPayload(orderId, "PAYMENT_FAILED", traceId, identity),
                    OutboxStatus.PENDING,
                    now,
                    null,
                    null
            ));
        }

        markConsumed(messageId, consumer);
    }

    private String createInventoryReleaseRequestedPayload(UUID orderId, String reason, UUID traceId, EventIdentity identity) {
        EventEnvelope<InventoryReleaseRequestedData> envelope = EventEnvelope.of("InventoryReleaseRequested",
                new InventoryReleaseRequestedData(orderId, reason),
                traceId,
                identity);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize InventoryReleaseRequested event", exception);
        }
    }

    private void markConsumed(String messageId, String consumer) {
        consumedMessageRepository.save(new ConsumedMessageEntity(
                UUID.randomUUID(),
                messageId,
                consumer,
                Instant.now()
        ));
    }
}
