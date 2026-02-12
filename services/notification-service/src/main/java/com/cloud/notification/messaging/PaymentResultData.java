package com.cloud.notification.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PaymentResultData(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("payment_id") UUID paymentId,
        String reason
) {
}
