package com.cloud.order;

import java.util.UUID;

class PaymentHashedFlowIntegrationTest extends AbstractEndToEndFlowIntegrationTest {

    @Override
    protected String paymentMockMode() {
        return "HASHED";
    }

    @Override
    protected String expectedOrderStatus(UUID orderId) {
        return hashedSuccess(orderId) ? "CONFIRMED" : "FAILED";
    }

    @Override
    protected String expectedPaymentStatus(UUID orderId) {
        return hashedSuccess(orderId) ? "SUCCEEDED" : "FAILED";
    }

    @Override
    protected String expectedInventoryStatus(UUID orderId) {
        return hashedSuccess(orderId) ? "RESERVED" : "RELEASED";
    }

    @Override
    protected int expectedInventoryReleaseEventCount(UUID orderId) {
        return hashedSuccess(orderId) ? 0 : 1;
    }

    private boolean hashedSuccess(UUID orderId) {
        return Math.floorMod(orderId.hashCode(), 2) == 0;
    }
}
