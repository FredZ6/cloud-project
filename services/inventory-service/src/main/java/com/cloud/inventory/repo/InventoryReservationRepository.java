package com.cloud.inventory.repo;

import com.cloud.inventory.domain.InventoryReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, UUID> {
    Optional<InventoryReservationEntity> findByOrderId(UUID orderId);
}
