package com.cloud.order;

import java.util.UUID;

class EndToEndFlowIntegrationTest extends AbstractEndToEndFlowIntegrationTest {

    @Override
    protected String paymentMockMode() {
        return "SUCCESS";
    }

    @Override
    protected String expectedOrderStatus(UUID orderId) {
        return "CONFIRMED";
    }

    @Override
    protected String expectedPaymentStatus(UUID orderId) {
        return "SUCCEEDED";
    }
}
