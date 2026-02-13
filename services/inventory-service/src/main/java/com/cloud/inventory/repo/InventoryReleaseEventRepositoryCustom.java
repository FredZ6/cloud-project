package com.cloud.inventory.repo;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface InventoryReleaseEventRepositoryCustom {

    List<InventoryReleaseEventEntity> findCursorPage(
            Specification<InventoryReleaseEventEntity> spec,
            Sort sort,
            int limit
    );
}

