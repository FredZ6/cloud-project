package com.cloud.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EventIdentity(
        @JsonProperty("user_id") String userId,
        List<String> roles
) {
}
