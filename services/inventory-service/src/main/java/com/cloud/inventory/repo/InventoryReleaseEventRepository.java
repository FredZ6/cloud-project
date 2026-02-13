package com.cloud.inventory.repo;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

public interface InventoryReleaseEventRepository extends
        JpaRepository<InventoryReleaseEventEntity, UUID>,
        JpaSpecificationExecutor<InventoryReleaseEventEntity>,
        InventoryReleaseEventRepositoryCustom {
}
