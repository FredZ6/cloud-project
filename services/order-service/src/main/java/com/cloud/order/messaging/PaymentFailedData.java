package com.cloud.order.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PaymentFailedData(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("payment_id") UUID paymentId,
        String reason
) {
}
