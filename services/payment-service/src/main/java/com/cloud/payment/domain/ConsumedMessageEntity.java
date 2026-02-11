package com.cloud.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumed_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_consumed_messages_message_consumer", columnNames = {"message_id", "consumer"})
})
public class ConsumedMessageEntity {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false, length = 128)
    private String messageId;

    @Column(nullable = false, length = 80)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ConsumedMessageEntity() {
    }

    public ConsumedMessageEntity(UUID id, String messageId, String consumer, Instant processedAt) {
        this.id = id;
        this.messageId = messageId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }
}
