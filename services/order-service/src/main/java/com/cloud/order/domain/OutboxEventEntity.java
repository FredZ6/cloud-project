package com.cloud.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 120)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEventEntity() {
    }

    public OutboxEventEntity(UUID id,
                             String eventType,
                             String routingKey,
                             String payload,
                             OutboxStatus status,
                             Instant createdAt,
                             Instant sentAt,
                             String lastError) {
        this.id = id;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.lastError = lastError;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markSent(Instant sentAt) {
        this.status = OutboxStatus.SENT;
        this.sentAt = sentAt;
        this.lastError = null;
    }

    public void notePublishError(String error) {
        this.lastError = error;
    }
}
