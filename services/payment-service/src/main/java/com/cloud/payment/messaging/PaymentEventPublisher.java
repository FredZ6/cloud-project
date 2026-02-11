package com.cloud.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.exchange:ecom.events}")
    private String eventsExchange;

    @Value("${app.messaging.routing-keys.payment-succeeded:payment.succeeded}")
    private String paymentSucceededRoutingKey;

    @Value("${app.messaging.routing-keys.payment-failed:payment.failed}")
    private String paymentFailedRoutingKey;

    @Value("${app.messaging.dlq-exchange:payment.dlq.exchange}")
    private String dlqExchange;

    @Value("${app.messaging.routing-keys.inventory-reserved-dlq:q.payment.inventory-reserved.dlq}")
    private String inventoryReservedDlqRoutingKey;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentSucceeded(UUID orderId, UUID paymentId, UUID traceId, EventIdentity identity) {
        PaymentSucceededData data = new PaymentSucceededData(orderId, paymentId);
        EventEnvelope<PaymentSucceededData> envelope = EventEnvelope.of("PaymentSucceeded", data, traceId, identity);
        publish(paymentSucceededRoutingKey, envelope);
    }

    public void publishPaymentFailed(UUID orderId, UUID paymentId, String reason, UUID traceId, EventIdentity identity) {
        PaymentFailedData data = new PaymentFailedData(orderId, paymentId, reason);
        EventEnvelope<PaymentFailedData> envelope = EventEnvelope.of("PaymentFailed", data, traceId, identity);
        publish(paymentFailedRoutingKey, envelope);
    }

    public void publishToDlq(Message originalMessage, String reason) {
        rabbitTemplate.convertAndSend(dlqExchange, inventoryReservedDlqRoutingKey, new String(originalMessage.getBody(), StandardCharsets.UTF_8), message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setHeader("x-dlq-reason", reason);
            if (originalMessage.getMessageProperties().getMessageId() != null) {
                message.getMessageProperties().setMessageId(originalMessage.getMessageProperties().getMessageId());
            }
            return message;
        });
    }

    private <T> void publish(String routingKey, EventEnvelope<T> envelope) {
        String payload = toJson(envelope);
        rabbitTemplate.convertAndSend(eventsExchange, routingKey, payload, withMessageId(envelope.eventId()));
    }

    private MessagePostProcessor withMessageId(UUID eventId) {
        return (Message message) -> {
            message.getMessageProperties().setMessageId(eventId.toString());
            message.getMessageProperties().setContentType("application/json");
            return message;
        };
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payment event", exception);
        }
    }
}
