package com.cloud.payment.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PaymentSucceededData(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("payment_id") UUID paymentId
) {
}
