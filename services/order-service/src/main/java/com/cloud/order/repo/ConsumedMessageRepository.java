package com.cloud.order.repo;

import com.cloud.order.domain.ConsumedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumedMessageRepository extends JpaRepository<ConsumedMessageEntity, UUID> {
    boolean existsByMessageIdAndConsumer(String messageId, String consumer);
}
