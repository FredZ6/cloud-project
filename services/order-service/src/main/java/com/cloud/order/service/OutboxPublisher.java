package com.cloud.order.service;

import com.cloud.order.domain.OutboxEventEntity;
import com.cloud.order.domain.OutboxStatus;
import com.cloud.order.repo.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.exchange:ecom.events}")
    private String exchange;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        for (OutboxEventEntity event : events) {
            try {
                rabbitTemplate.convertAndSend(exchange, event.getRoutingKey(), event.getPayload(), messagePostProcessor(event));
                event.markSent(Instant.now());
                log.debug("Published outbox event id={} type={} routingKey={} traceId={}",
                        event.getId(), event.getEventType(), event.getRoutingKey(), extractTraceId(event.getPayload()).orElse("n/a"));
            } catch (RuntimeException exception) {
                event.notePublishError(shortError(exception));
            }
        }
    }

    private MessagePostProcessor messagePostProcessor(OutboxEventEntity event) {
        return (Message message) -> {
            message.getMessageProperties().setMessageId(event.getId().toString());
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setHeader("x-event-type", event.getEventType());
            extractTraceId(event.getPayload()).ifPresent(traceId -> {
                message.getMessageProperties().setHeader("x-trace-id", traceId);
                toTraceParent(traceId).ifPresent(traceParent -> message.getMessageProperties().setHeader("traceparent", traceParent));
            });
            return message;
        };
    }

    private java.util.Optional<String> extractTraceId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String traceId = root.path("trace_id").asText(null);
            if (traceId == null || traceId.isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(traceId.trim());
        } catch (Exception exception) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<String> toTraceParent(String traceId) {
        String normalized = normalizeTraceId(traceId);
        if (normalized == null) {
            return java.util.Optional.empty();
        }
        String spanId = randomSpanId();
        return java.util.Optional.of("00-" + normalized + "-" + spanId + "-01");
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        String value = traceId.trim().toLowerCase(Locale.ROOT);
        if (value.length() == 32 && value.matches("[0-9a-f]{32}")) {
            return value;
        }
        try {
            UUID uuid = UUID.fromString(value);
            return String.format("%016x%016x", uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String randomSpanId() {
        long value = ThreadLocalRandom.current().nextLong();
        if (value == 0L) {
            value = 1L;
        }
        return String.format("%016x", value);
    }

    private String shortError(RuntimeException exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
