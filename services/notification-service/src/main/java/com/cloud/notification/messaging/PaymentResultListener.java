package com.cloud.notification.messaging;

import com.cloud.notification.domain.NotificationEvent;
import com.cloud.notification.service.NotificationStoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationStoreService storeService;

    public PaymentResultListener(ObjectMapper objectMapper, NotificationStoreService storeService) {
        this.objectMapper = objectMapper;
        this.storeService = storeService;
    }

    @RabbitListener(queues = "${app.messaging.queues.payment-result:q.notification.payment-result}")
    public void handlePaymentResult(String raw) {
        try {
            EventEnvelope<PaymentResultData> envelope = objectMapper.readValue(raw, new TypeReference<>() {
            });
            bindTraceToMdc(envelope.traceId());
            PaymentResultData data = envelope.data();
            NotificationEvent event = new NotificationEvent(
                    envelope.eventId(),
                    envelope.eventType(),
                    data == null ? null : data.orderId(),
                    data == null ? null : data.paymentId(),
                    data == null ? null : data.reason(),
                    envelope.traceId(),
                    envelope.identity() == null ? null : envelope.identity().userId(),
                    envelope.identity() == null || envelope.identity().roles() == null ? List.of() : envelope.identity().roles(),
                    envelope.occurredAt() == null ? Instant.now() : envelope.occurredAt(),
                    Instant.now()
            );
            storeService.save(event);
        } catch (JsonProcessingException exception) {
            log.warn("Skip malformed payment result event: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Notification event processing failed", exception);
            throw exception;
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void bindTraceToMdc(java.util.UUID traceId) {
        if (traceId == null) {
            return;
        }
        MDC.put("trace_id", traceId.toString());
    }
}
