package com.cloud.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "inventory_reservation_items")
public class InventoryReservationItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservationEntity reservation;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(nullable = false)
    private Integer quantity;

    protected InventoryReservationItemEntity() {
    }

    public InventoryReservationItemEntity(UUID id, String skuId, Integer quantity) {
        this.id = id;
        this.skuId = skuId;
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public InventoryReservationEntity getReservation() {
        return reservation;
    }

    public void setReservation(InventoryReservationEntity reservation) {
        this.reservation = reservation;
    }

    public String getSkuId() {
        return skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
