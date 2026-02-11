package com.cloud.inventory.repo;

import com.cloud.inventory.domain.SkuStockEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface SkuStockRepository extends JpaRepository<SkuStockEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SkuStockEntity s where s.skuId in :skuIds")
    List<SkuStockEntity> findAllBySkuIdInForUpdate(@Param("skuIds") Set<String> skuIds);
}
