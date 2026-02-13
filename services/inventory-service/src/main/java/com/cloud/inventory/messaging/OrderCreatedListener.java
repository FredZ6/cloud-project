package com.cloud.inventory.messaging;

import com.cloud.inventory.service.ConsumedMessageService;
import com.cloud.inventory.service.InventoryReservationService;
import com.cloud.inventory.service.ReservationOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderCreatedListener {

    private static final String CONSUMER_NAME = "inventory.order-created";

    private final ObjectMapper objectMapper;
    private final InventoryReservationService reservationService;
    private final ConsumedMessageService consumedMessageService;
    private final InventoryEventPublisher eventPublisher;

    @Value("${app.messaging.queues.order-created:q.inventory.order-created}")
    private String orderCreatedQueue;

    @Value("${app.messaging.max-retries:3}")
    private int maxRetries;

    public OrderCreatedListener(ObjectMapper objectMapper,
                                InventoryReservationService reservationService,
                                ConsumedMessageService consumedMessageService,
                                InventoryEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
        this.consumedMessageService = consumedMessageService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @RabbitListener(queues = "${app.messaging.queues.order-created:q.inventory.order-created}")
    public void handleOrderCreated(Message message) {
        long retryCount = extractRetryCount(message);
        String raw = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            EventEnvelope<OrderCreatedData> envelope = parse(raw);
            bindTraceToMdc(envelope.traceId());
            String messageId = resolveMessageId(message, envelope);
            if (consumedMessageService.isConsumed(messageId, CONSUMER_NAME)) {
                return;
            }

            List<ReservationOutcome.ReservedItem> items = envelope.data().items().stream()
                    .map(item -> new ReservationOutcome.ReservedItem(item.skuId(), item.quantity()))
                    .toList();

            ReservationOutcome outcome = reservationService.reserveForOrder(envelope.data().orderId(), items);
            eventPublisher.publishReservationResult(outcome, envelope.traceId(), envelope.identity());
            consumedMessageService.markConsumed(messageId, CONSUMER_NAME);
        } catch (RuntimeException exception) {
            if (retryCount >= maxRetries) {
                eventPublisher.publishOrderCreatedToDlq(message, "RETRY_EXHAUSTED:" + shortError(exception));
                return;
            }
            throw new AmqpRejectAndDontRequeueException("Transient inventory processing failure", exception);
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void bindTraceToMdc(UUID traceId) {
        if (traceId == null) {
            return;
        }
        MDC.put("trace_id", traceId.toString());
    }

    private EventEnvelope<OrderCreatedData> parse(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid order.created payload", exception);
        }
    }

    private String resolveMessageId(Message message, EventEnvelope<OrderCreatedData> envelope) {
        if (message.getMessageProperties().getMessageId() != null && !message.getMessageProperties().getMessageId().isBlank()) {
            return message.getMessageProperties().getMessageId();
        }
        UUID fallback = envelope.eventId() == null ? UUID.randomUUID() : envelope.eventId();
        return fallback.toString();
    }

    private long extractRetryCount(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath == null) {
            return 0;
        }
        for (Map<String, ?> entry : xDeath) {
            String queue = asString(entry.get("queue"));
            if (orderCreatedQueue.equals(queue)) {
                Object count = entry.get("count");
                if (count instanceof Number number) {
                    return number.longValue();
                }
            }
        }
        return 0;
    }

    private String asString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private String shortError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
