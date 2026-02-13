package com.cloud.notification.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EventIdentity(
        @JsonProperty("user_id") String userId,
        @JsonProperty("roles") List<String> roles
) {
}
