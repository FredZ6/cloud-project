package com.cloud.inventory.service;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import com.cloud.inventory.repo.InventoryReleaseEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryReleaseAuditService {

    private static final int MAX_EXPORT_LIMIT = 10_000;

    private final InventoryReleaseEventRepository inventoryReleaseEventRepository;

    public InventoryReleaseAuditService(InventoryReleaseEventRepository inventoryReleaseEventRepository) {
        this.inventoryReleaseEventRepository = inventoryReleaseEventRepository;
    }

    public record ReleaseEventsCursorPage(
            List<InventoryReleaseEventEntity> items,
            boolean hasMore,
            String nextCursor
    ) {
    }

    @Transactional(readOnly = true)
    public Page<InventoryReleaseEventEntity> listReleaseEvents(UUID orderId, Instant from, Instant to, int page, int size) {
        validateTimeRange(from, to);
        return inventoryReleaseEventRepository.findAll(buildSpec(orderId, from, to), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public ReleaseEventsCursorPage listReleaseEventsCursor(UUID orderId, Instant from, Instant to, int size, String after) {
        validateTimeRange(from, to);

        Specification<InventoryReleaseEventEntity> spec = buildSpec(orderId, from, to);
        if (after != null && !after.isBlank()) {
            ReleaseEventsCursor.Decoded decoded = ReleaseEventsCursor.decode(after);
            spec = spec.and(buildAfterSpec(decoded));
        }

        Page<InventoryReleaseEventEntity> page = inventoryReleaseEventRepository.findAll(
                spec,
                PageRequest.of(
                        0,
                        size + 1,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                )
        );

        List<InventoryReleaseEventEntity> content = page.getContent();
        boolean hasMore = content.size() > size;
        List<InventoryReleaseEventEntity> items = hasMore ? content.subList(0, size) : content;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            InventoryReleaseEventEntity last = items.get(items.size() - 1);
            nextCursor = ReleaseEventsCursor.encode(last.getCreatedAt(), last.getId());
        }

        return new ReleaseEventsCursorPage(List.copyOf(items), hasMore, nextCursor);
    }

    @Transactional(readOnly = true)
    public String exportReleaseEventsCsv(UUID orderId, Instant from, Instant to, int limit) {
        validateTimeRange(from, to);
        if (limit < 1 || limit > MAX_EXPORT_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + MAX_EXPORT_LIMIT
            );
        }

        Page<InventoryReleaseEventEntity> page = inventoryReleaseEventRepository.findAll(
                buildSpec(orderId, from, to),
                PageRequest.of(0, limit)
        );
        StringBuilder csv = new StringBuilder();
        csv.append("release_id,order_id,reservation_id,reason,created_at\n");

        for (InventoryReleaseEventEntity event : page.getContent()) {
            csv.append(event.getId()).append(',')
                    .append(event.getOrderId()).append(',')
                    .append(event.getReservationId()).append(',')
                    .append(csvEscape(event.getReason())).append(',')
                    .append(event.getCreatedAt())
                    .append('\n');
        }
        return csv.toString();
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be <= to");
        }
    }

    private Specification<InventoryReleaseEventEntity> buildAfterSpec(ReleaseEventsCursor.Decoded after) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), after.createdAt()),
                cb.and(
                        cb.equal(root.get("createdAt"), after.createdAt()),
                        cb.lessThan(root.get("id"), after.id())
                )
        );
    }

    private Specification<InventoryReleaseEventEntity> buildSpec(UUID orderId, Instant from, Instant to) {
        Specification<InventoryReleaseEventEntity> spec = Specification.where(null);
        if (orderId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("orderId"), orderId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return spec;
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
