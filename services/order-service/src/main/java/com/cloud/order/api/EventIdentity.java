package com.cloud.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EventIdentity(
        @JsonProperty("user_id") String userId,
        List<String> roles
) {
}
