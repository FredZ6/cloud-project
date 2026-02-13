package com.cloud.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SyntheticNotificationRequest(
        @NotNull UUID orderId,
        @NotNull UUID paymentId,
        @NotBlank String reason
) {
}
