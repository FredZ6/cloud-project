package com.cloud.order;

import java.util.UUID;

class PaymentFailureFlowIntegrationTest extends AbstractEndToEndFlowIntegrationTest {

    @Override
    protected String paymentMockMode() {
        return "FAIL";
    }

    @Override
    protected String expectedOrderStatus(UUID orderId) {
        return "FAILED";
    }

    @Override
    protected String expectedPaymentStatus(UUID orderId) {
        return "FAILED";
    }

    @Override
    protected String expectedInventoryStatus(UUID orderId) {
        return "RELEASED";
    }

    @Override
    protected int expectedInventoryReleaseEventCount(UUID orderId) {
        return 1;
    }
}
