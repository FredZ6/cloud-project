package com.cloud.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sku_stocks")
public class SkuStockEntity {

    @Id
    @Column(name = "sku_id", length = 64)
    private String skuId;

    @Column(name = "available_qty", nullable = false)
    private Integer availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkuStockEntity() {
    }

    public SkuStockEntity(String skuId, Integer availableQty, Integer reservedQty, Instant updatedAt) {
        this.skuId = skuId;
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.updatedAt = updatedAt;
    }

    public String getSkuId() {
        return skuId;
    }

    public Integer getAvailableQty() {
        return availableQty;
    }

    public Integer getReservedQty() {
        return reservedQty;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setAvailableQty(Integer availableQty) {
        this.availableQty = availableQty;
        this.updatedAt = Instant.now();
    }

    public void reserve(int qty) {
        this.availableQty = this.availableQty - qty;
        this.reservedQty = this.reservedQty + qty;
        this.updatedAt = Instant.now();
    }

    public void release(int qty) {
        if (qty < 0) {
            throw new IllegalArgumentException("Release quantity must be non-negative");
        }
        if (this.reservedQty < qty) {
            throw new IllegalStateException("Cannot release more than reserved for sku: " + skuId);
        }
        this.availableQty = this.availableQty + qty;
        this.reservedQty = this.reservedQty - qty;
        this.updatedAt = Instant.now();
    }
}
