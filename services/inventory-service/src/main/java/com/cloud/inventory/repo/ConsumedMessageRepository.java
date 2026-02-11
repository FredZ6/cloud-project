package com.cloud.inventory.repo;

import com.cloud.inventory.domain.ConsumedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsumedMessageRepository extends JpaRepository<ConsumedMessageEntity, UUID> {
    boolean existsByMessageIdAndConsumer(String messageId, String consumer);

    Optional<ConsumedMessageEntity> findByMessageIdAndConsumer(String messageId, String consumer);
}
