package com.cloud.order.messaging;

import com.cloud.order.api.EventEnvelope;
import com.cloud.order.service.OrderStatusUpdateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PaymentResultListener {

    private final ObjectMapper objectMapper;
    private final OrderStatusUpdateService orderStatusUpdateService;

    public PaymentResultListener(ObjectMapper objectMapper, OrderStatusUpdateService orderStatusUpdateService) {
        this.objectMapper = objectMapper;
        this.orderStatusUpdateService = orderStatusUpdateService;
    }

    @RabbitListener(queues = "${app.messaging.queues.payment-result:q.order.payment-result}")
    public void handlePaymentResult(Message message) {
        String raw = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = parseRoot(raw);
        String eventType = root.path("event_type").asText();

        if ("PaymentSucceeded".equals(eventType)) {
            EventEnvelope<PaymentSucceededData> envelope = parseSucceeded(raw);
            String messageId = resolveMessageId(message, envelope.eventId());
            orderStatusUpdateService.markPaymentSucceeded(messageId, envelope.data().orderId());
            return;
        }

        if ("PaymentFailed".equals(eventType)) {
            EventEnvelope<PaymentFailedData> envelope = parseFailed(raw);
            String messageId = resolveMessageId(message, envelope.eventId());
            orderStatusUpdateService.markPaymentFailed(messageId, envelope.data().orderId(), envelope.traceId(), envelope.identity());
            return;
        }

        throw new AmqpRejectAndDontRequeueException("Unsupported payment event type: " + eventType);
    }

    private JsonNode parseRoot(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid payment result payload", exception);
        }
    }

    private EventEnvelope<PaymentSucceededData> parseSucceeded(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid PaymentSucceeded payload", exception);
        }
    }

    private EventEnvelope<PaymentFailedData> parseFailed(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid PaymentFailed payload", exception);
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
