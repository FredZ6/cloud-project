package com.cloud.payment.messaging;

import com.cloud.payment.service.PaymentProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class InventoryReservedListener {

    private final ObjectMapper objectMapper;
    private final PaymentProcessingService paymentProcessingService;
    private final PaymentEventPublisher paymentEventPublisher;

    @Value("${app.messaging.queues.inventory-reserved:q.payment.inventory-reserved}")
    private String inventoryReservedQueue;

    @Value("${app.messaging.max-retries:3}")
    private int maxRetries;

    public InventoryReservedListener(ObjectMapper objectMapper,
                                     PaymentProcessingService paymentProcessingService,
                                     PaymentEventPublisher paymentEventPublisher) {
        this.objectMapper = objectMapper;
        this.paymentProcessingService = paymentProcessingService;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @RabbitListener(queues = "${app.messaging.queues.inventory-reserved:q.payment.inventory-reserved}")
    public void handleInventoryReserved(Message message) {
        long retryCount = extractRetryCount(message);
        String raw = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            EventEnvelope<InventoryReservedData> envelope = parse(raw);
            String messageId = resolveMessageId(message, envelope.eventId());
            paymentProcessingService.processInventoryReserved(messageId, envelope);
        } catch (RuntimeException exception) {
            if (retryCount >= maxRetries) {
                paymentEventPublisher.publishToDlq(message, "RETRY_EXHAUSTED:" + shortError(exception));
                return;
            }
            throw new AmqpRejectAndDontRequeueException("Transient payment processing failure", exception);
        }
    }

    private EventEnvelope<InventoryReservedData> parse(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid inventory.reserved payload", exception);
        }
    }

    private String resolveMessageId(Message message, UUID fallbackEventId) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        return fallbackEventId == null ? UUID.randomUUID().toString() : fallbackEventId.toString();
    }

    private long extractRetryCount(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath == null) {
            return 0;
        }
        for (Map<String, ?> entry : xDeath) {
            String queue = asString(entry.get("queue"));
            if (inventoryReservedQueue.equals(queue)) {
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
