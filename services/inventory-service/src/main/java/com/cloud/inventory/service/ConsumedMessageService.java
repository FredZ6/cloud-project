package com.cloud.inventory.service;

import com.cloud.inventory.domain.ConsumedMessageEntity;
import com.cloud.inventory.repo.ConsumedMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ConsumedMessageService {

    private final ConsumedMessageRepository consumedMessageRepository;

    public ConsumedMessageService(ConsumedMessageRepository consumedMessageRepository) {
        this.consumedMessageRepository = consumedMessageRepository;
    }

    @Transactional(readOnly = true)
    public boolean isConsumed(String messageId, String consumer) {
        return consumedMessageRepository.existsByMessageIdAndConsumer(messageId, consumer);
    }

    @Transactional
    public void markConsumed(String messageId, String consumer) {
        if (consumedMessageRepository.existsByMessageIdAndConsumer(messageId, consumer)) {
            return;
        }
        consumedMessageRepository.save(new ConsumedMessageEntity(
                UUID.randomUUID(),
                messageId,
                consumer,
                Instant.now()
        ));
    }
}
