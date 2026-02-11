package com.cloud.order.service;

import com.cloud.order.domain.OutboxEventEntity;
import com.cloud.order.domain.OutboxStatus;
import com.cloud.order.repo.OutboxEventRepository;
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

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.messaging.exchange:ecom.events}")
    private String exchange;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
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
            } catch (RuntimeException exception) {
                event.notePublishError(shortError(exception));
            }
        }
    }

    private MessagePostProcessor messagePostProcessor(OutboxEventEntity event) {
        return (Message message) -> {
            message.getMessageProperties().setMessageId(event.getId().toString());
            message.getMessageProperties().setContentType("application/json");
            return message;
        };
    }

    private String shortError(RuntimeException exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
