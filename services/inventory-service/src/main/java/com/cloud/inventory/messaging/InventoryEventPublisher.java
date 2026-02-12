package com.cloud.inventory.messaging;

import com.cloud.inventory.domain.ReservationStatus;
import com.cloud.inventory.service.InventoryReleaseOutcome;
import com.cloud.inventory.service.ReservationOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.exchange:ecom.events}")
    private String eventsExchange;

    @Value("${app.messaging.dlq-exchange:inventory.dlq.exchange}")
    private String dlqExchange;

    @Value("${app.messaging.routing-keys.inventory-reserved:inventory.reserved}")
    private String inventoryReservedRoutingKey;

    @Value("${app.messaging.routing-keys.inventory-failed:inventory.failed}")
    private String inventoryFailedRoutingKey;

    @Value("${app.messaging.routing-keys.inventory-released:inventory.released}")
    private String inventoryReleasedRoutingKey;

    @Value("${app.messaging.routing-keys.order-created-dlq:q.inventory.order-created.dlq}")
    private String orderCreatedDlqRoutingKey;

    @Value("${app.messaging.routing-keys.release-requested-dlq:q.inventory.release-requested.dlq}")
    private String releaseRequestedDlqRoutingKey;

    public InventoryEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishReservationResult(ReservationOutcome outcome, UUID traceId, EventIdentity identity) {
        if (outcome.status() == ReservationStatus.RESERVED) {
            publishReserved(outcome, traceId, identity);
            return;
        }
        publishFailed(outcome.orderId(), outcome.reservationId(), outcome.reason(), traceId, identity);
    }

    public void publishFailed(UUID orderId, UUID reservationId, String reason, UUID traceId, EventIdentity identity) {
        InventoryFailedData data = new InventoryFailedData(orderId, reservationId, reason);
        EventEnvelope<InventoryFailedData> envelope = EventEnvelope.of("InventoryFailed", data, traceId, identity);
        publishToEvents(inventoryFailedRoutingKey, envelope);
    }

    public void publishReleased(InventoryReleaseOutcome outcome, UUID traceId, EventIdentity identity) {
        List<InventoryReleasedItem> items = outcome.items().stream()
                .map(item -> new InventoryReleasedItem(item.skuId(), item.quantity()))
                .toList();
        InventoryReleasedData data = new InventoryReleasedData(
                outcome.releaseId(),
                outcome.orderId(),
                outcome.reservationId(),
                outcome.reason(),
                items
        );
        EventEnvelope<InventoryReleasedData> envelope = EventEnvelope.of("InventoryReleased", data, traceId, identity);
        publishToEvents(inventoryReleasedRoutingKey, envelope);
    }

    public void publishOrderCreatedToDlq(Message originalMessage, String reason) {
        publishToDlq(originalMessage, orderCreatedDlqRoutingKey, reason);
    }

    public void publishReleaseRequestedToDlq(Message originalMessage, String reason) {
        publishToDlq(originalMessage, releaseRequestedDlqRoutingKey, reason);
    }

    private void publishToDlq(Message originalMessage, String routingKey, String reason) {
        rabbitTemplate.convertAndSend(dlqExchange, routingKey, new String(originalMessage.getBody(), StandardCharsets.UTF_8), message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setHeader("x-dlq-reason", reason);
            if (originalMessage.getMessageProperties().getMessageId() != null) {
                message.getMessageProperties().setMessageId(originalMessage.getMessageProperties().getMessageId());
            }
            copyHeaderIfPresent(originalMessage, message, "x-trace-id");
            copyHeaderIfPresent(originalMessage, message, "x-event-type");
            return message;
        });
    }

    private void publishReserved(ReservationOutcome outcome, UUID traceId, EventIdentity identity) {
        List<InventoryReservedItem> items = outcome.items().stream()
                .map(item -> new InventoryReservedItem(item.skuId(), item.quantity()))
                .toList();
        InventoryReservedData data = new InventoryReservedData(outcome.orderId(), outcome.reservationId(), items);
        EventEnvelope<InventoryReservedData> envelope = EventEnvelope.of("InventoryReserved", data, traceId, identity);
        publishToEvents(inventoryReservedRoutingKey, envelope);
    }

    private <T> void publishToEvents(String routingKey, EventEnvelope<T> envelope) {
        String payload = toJson(envelope);
        rabbitTemplate.convertAndSend(eventsExchange, routingKey, payload,
                withMessageId(envelope.eventId(), envelope.eventType(), envelope.traceId()));
        log.debug("Published event type={} routingKey={} eventId={} traceId={}",
                envelope.eventType(), routingKey, envelope.eventId(), envelope.traceId());
    }

    private MessagePostProcessor withMessageId(UUID eventId, String eventType, UUID traceId) {
        return (Message message) -> {
            message.getMessageProperties().setMessageId(eventId.toString());
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setHeader("x-event-id", eventId.toString());
            message.getMessageProperties().setHeader("x-event-type", eventType);
            if (traceId != null) {
                message.getMessageProperties().setHeader("x-trace-id", traceId.toString());
            }
            return message;
        };
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event payload", exception);
        }
    }

    private void copyHeaderIfPresent(Message source, Message target, String headerName) {
        Object value = source.getMessageProperties().getHeaders().get(headerName);
        if (value != null) {
            target.getMessageProperties().setHeader(headerName, value);
        }
    }
}
