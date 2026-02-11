package com.cloud.payment.repo;

import com.cloud.payment.domain.PaymentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecordEntity, UUID> {
    Optional<PaymentRecordEntity> findByOrderId(UUID orderId);
}
