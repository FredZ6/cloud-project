package com.cloud.order.messaging;

import com.cloud.order.api.EventEnvelope;
import com.cloud.order.service.OrderStatusUpdateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class InventoryResultListener {

    private final ObjectMapper objectMapper;
    private final OrderStatusUpdateService orderStatusUpdateService;

    public InventoryResultListener(ObjectMapper objectMapper, OrderStatusUpdateService orderStatusUpdateService) {
        this.objectMapper = objectMapper;
        this.orderStatusUpdateService = orderStatusUpdateService;
    }

    @RabbitListener(queues = "${app.messaging.queues.inventory-result:q.order.inventory-result}")
    public void handleInventoryResult(Message message) {
        String raw = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = parseRoot(raw);
        String eventType = root.path("event_type").asText();
        bindTraceToMdc(root.path("trace_id").asText(null));

        try {
            if ("InventoryReserved".equals(eventType)) {
                EventEnvelope<InventoryReservedData> envelope = parseReserved(raw);
                String messageId = resolveMessageId(message, envelope.eventId());
                orderStatusUpdateService.markReserved(messageId, envelope.data().orderId());
                return;
            }

            if ("InventoryFailed".equals(eventType)) {
                EventEnvelope<InventoryFailedData> envelope = parseFailed(raw);
                String messageId = resolveMessageId(message, envelope.eventId());
                orderStatusUpdateService.markInventoryFailed(messageId, envelope.data().orderId());
                return;
            }

            if ("InventoryReleased".equals(eventType)) {
                EventEnvelope<InventoryReleasedData> envelope = parseReleased(raw);
                String messageId = resolveMessageId(message, envelope.eventId());
                orderStatusUpdateService.markInventoryReleased(messageId, envelope.data().orderId());
                return;
            }
        } finally {
            MDC.remove("trace_id");
        }

        throw new AmqpRejectAndDontRequeueException("Unsupported inventory event type: " + eventType);
    }

    private void bindTraceToMdc(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        MDC.put("trace_id", traceId.trim());
    }

    private JsonNode parseRoot(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid inventory result payload", exception);
        }
    }

    private EventEnvelope<InventoryReservedData> parseReserved(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid InventoryReserved payload", exception);
        }
    }

    private EventEnvelope<InventoryFailedData> parseFailed(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid InventoryFailed payload", exception);
        }
    }

    private EventEnvelope<InventoryReleasedData> parseReleased(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid InventoryReleased payload", exception);
        }
    }

    private String resolveMessageId(Message message, UUID fallbackEventId) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        return fallbackEventId == null ? UUID.randomUUID().toString() : fallbackEventId.toString();
    }
}
