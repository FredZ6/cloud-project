package com.cloud.payment.service;

import com.cloud.payment.domain.ConsumedMessageEntity;
import com.cloud.payment.domain.PaymentRecordEntity;
import com.cloud.payment.domain.PaymentStatus;
import com.cloud.payment.messaging.EventEnvelope;
import com.cloud.payment.messaging.InventoryReservedData;
import com.cloud.payment.messaging.PaymentEventPublisher;
import com.cloud.payment.repo.ConsumedMessageRepository;
import com.cloud.payment.repo.PaymentRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentProcessingService {

    private static final String CONSUMER_NAME = "payment.inventory-reserved";

    private final PaymentRecordRepository paymentRecordRepository;
    private final ConsumedMessageRepository consumedMessageRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    @Value("${app.payment.mock-mode:SUCCESS}")
    private String mockModeValue;

    @Value("${app.payment.failure-reason:MOCK_DECLINED}")
    private String failureReason;

    public PaymentProcessingService(PaymentRecordRepository paymentRecordRepository,
                                    ConsumedMessageRepository consumedMessageRepository,
                                    PaymentEventPublisher paymentEventPublisher) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.consumedMessageRepository = consumedMessageRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Transactional
    public void processInventoryReserved(String messageId, EventEnvelope<InventoryReservedData> envelope) {
        InventoryReservedData data = envelope.data();
        if (consumedMessageRepository.existsByMessageIdAndConsumer(messageId, CONSUMER_NAME)) {
            return;
        }

        PaymentRecordEntity record = paymentRecordRepository.findByOrderId(data.orderId())
                .orElseGet(() -> createPaymentRecord(data.orderId()));

        if (record.getStatus() == PaymentStatus.SUCCEEDED) {
            paymentEventPublisher.publishPaymentSucceeded(record.getOrderId(), record.getId(), envelope.traceId(), envelope.identity());
            markConsumed(messageId);
            return;
        }

        if (record.getStatus() == PaymentStatus.FAILED) {
            paymentEventPublisher.publishPaymentFailed(record.getOrderId(), record.getId(), record.getReason(), envelope.traceId(), envelope.identity());
            markConsumed(messageId);
            return;
        }

        throw new IllegalStateException("Unsupported payment status");
    }

    private PaymentRecordEntity createPaymentRecord(UUID orderId) {
        PaymentMockMode mode = parseMode(mockModeValue);
        boolean success = decideSuccess(mode, orderId);

        PaymentRecordEntity record = new PaymentRecordEntity(
                UUID.randomUUID(),
                orderId,
                success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED,
                success ? null : failureReason,
                Instant.now()
        );

        return paymentRecordRepository.save(record);
    }

    private void markConsumed(String messageId) {
        consumedMessageRepository.save(new ConsumedMessageEntity(
                UUID.randomUUID(),
                messageId,
                CONSUMER_NAME,
                Instant.now()
        ));
    }

    private PaymentMockMode parseMode(String value) {
        if (value == null) {
            return PaymentMockMode.SUCCESS;
        }
        try {
            return PaymentMockMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return PaymentMockMode.SUCCESS;
        }
    }

    private boolean decideSuccess(PaymentMockMode mode, UUID orderId) {
        return switch (mode) {
            case SUCCESS -> true;
            case FAIL -> false;
            case HASHED -> Math.floorMod(orderId.hashCode(), 2) == 0;
        };
    }
}
