package com.cloud.order.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class TraceIdContextResolver {

    private final Tracer tracer;

    public TraceIdContextResolver(Tracer tracer) {
        this.tracer = tracer;
    }

    public UUID resolveOrRandom() {
        return resolveCurrent().orElseGet(UUID::randomUUID);
    }

    public Optional<UUID> resolveCurrent() {
        String traceId = resolveCurrentTraceIdString();
        if (traceId == null || traceId.isBlank()) {
            return Optional.empty();
        }
        return parseUuid(traceId.trim());
    }

    private String resolveCurrentTraceIdString() {
        Span span = tracer.currentSpan();
        if (span != null && span.context() != null && span.context().traceId() != null && !span.context().traceId().isBlank()) {
            return span.context().traceId();
        }
        return MDC.get("trace_id");
    }

    private Optional<UUID> parseUuid(String traceId) {
        try {
            if (traceId.contains("-")) {
                return Optional.of(UUID.fromString(traceId));
            }
            if (traceId.length() == 32) {
                long msb = Long.parseUnsignedLong(traceId.substring(0, 16), 16);
                long lsb = Long.parseUnsignedLong(traceId.substring(16), 16);
                return Optional.of(new UUID(msb, lsb));
            }
            if (traceId.length() == 16) {
                long lsb = Long.parseUnsignedLong(traceId, 16);
                return Optional.of(new UUID(0L, lsb));
            }
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
