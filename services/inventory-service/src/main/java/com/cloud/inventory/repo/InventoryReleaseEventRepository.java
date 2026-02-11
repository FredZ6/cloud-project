package com.cloud.inventory.repo;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InventoryReleaseEventRepository extends JpaRepository<InventoryReleaseEventEntity, UUID> {

    @Query("""
            SELECT e
            FROM InventoryReleaseEventEntity e
            WHERE (:orderId IS NULL OR e.orderId = :orderId)
              AND (:fromTime IS NULL OR e.createdAt >= :fromTime)
              AND (:toTime IS NULL OR e.createdAt <= :toTime)
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    Page<InventoryReleaseEventEntity> search(
            @Param("orderId") UUID orderId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );
}
