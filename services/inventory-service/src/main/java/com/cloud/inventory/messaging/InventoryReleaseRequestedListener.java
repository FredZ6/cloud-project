package com.cloud.inventory.messaging;

import com.cloud.inventory.service.ConsumedMessageService;
import com.cloud.inventory.service.InventoryReleaseOutcome;
import com.cloud.inventory.service.InventoryReservationService;
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
public class InventoryReleaseRequestedListener {

    private static final String CONSUMER_NAME = "inventory.release-requested";

    private final ObjectMapper objectMapper;
    private final InventoryReservationService inventoryReservationService;
    private final ConsumedMessageService consumedMessageService;
    private final InventoryEventPublisher eventPublisher;

    @Value("${app.messaging.queues.release-requested:q.inventory.release-requested}")
    private String releaseRequestedQueue;

    @Value("${app.messaging.max-retries:3}")
    private int maxRetries;

    public InventoryReleaseRequestedListener(ObjectMapper objectMapper,
                                             InventoryReservationService inventoryReservationService,
                                             ConsumedMessageService consumedMessageService,
                                             InventoryEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.inventoryReservationService = inventoryReservationService;
        this.consumedMessageService = consumedMessageService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @RabbitListener(queues = "${app.messaging.queues.release-requested:q.inventory.release-requested}")
    public void handleReleaseRequested(Message message) {
        long retryCount = extractRetryCount(message);
        String raw = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            EventEnvelope<InventoryReleaseRequestedData> envelope = parse(raw);
            bindTraceToMdc(envelope.traceId());
            String messageId = resolveMessageId(message, envelope);
            if (consumedMessageService.isConsumed(messageId, CONSUMER_NAME)) {
                return;
            }

            var releaseOutcome = inventoryReservationService.releaseReservationForOrder(envelope.data().orderId(), envelope.data().reason());
            releaseOutcome.ifPresent(outcome -> eventPublisher.publishReleased(outcome, envelope.traceId(), envelope.identity()));
            consumedMessageService.markConsumed(messageId, CONSUMER_NAME);
        } catch (RuntimeException exception) {
            if (retryCount >= maxRetries) {
                eventPublisher.publishReleaseRequestedToDlq(message, "RETRY_EXHAUSTED:" + shortError(exception));
                return;
            }
            throw new AmqpRejectAndDontRequeueException("Transient inventory release processing failure", exception);
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

    private EventEnvelope<InventoryReleaseRequestedData> parse(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid inventory.release.requested payload", exception);
        }
    }

    private String resolveMessageId(Message message, EventEnvelope<InventoryReleaseRequestedData> envelope) {
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
            if (releaseRequestedQueue.equals(queue)) {
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
