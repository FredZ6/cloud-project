package com.cloud.notification.service;

import com.cloud.notification.domain.NotificationEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class NotificationStoreService {

    private static final int MAX_EVENTS = 1000;

    private final ConcurrentLinkedDeque<NotificationEvent> events = new ConcurrentLinkedDeque<>();

    public void save(NotificationEvent event) {
        events.addFirst(event);
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }

    public List<NotificationEvent> query(UUID orderId, String eventType, int size) {
        return events.stream()
                .filter(event -> orderId == null || orderId.equals(event.orderId()))
                .filter(event -> eventType == null || eventType.isBlank() || eventType.equalsIgnoreCase(event.eventType()))
                .sorted(Comparator.comparing(NotificationEvent::receivedAt).reversed())
                .limit(size)
                .toList();
    }

    public NotificationEvent appendSyntheticFailure(UUID orderId, UUID paymentId, String reason) {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                "PaymentFailed",
                orderId,
                paymentId,
                reason,
                UUID.randomUUID(),
                "system",
                List.of("system"),
                Instant.now(),
                Instant.now()
        );
        save(event);
        return event;
    }

    public int count() {
        return events.size();
    }
}
