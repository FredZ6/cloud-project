package com.cloud.inventory.repo;

import com.cloud.inventory.domain.InventoryReleaseEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InventoryReleaseEventRepositoryImpl implements InventoryReleaseEventRepositoryCustom {

    private final EntityManager entityManager;

    public InventoryReleaseEventRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<InventoryReleaseEventEntity> findCursorPage(
            Specification<InventoryReleaseEventEntity> spec,
            Sort sort,
            int limit
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<InventoryReleaseEventEntity> query = cb.createQuery(InventoryReleaseEventEntity.class);
        Root<InventoryReleaseEventEntity> root = query.from(InventoryReleaseEventEntity.class);
        query.select(root);

        if (spec != null) {
            var predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }

        if (sort != null && sort.isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order order : sort) {
                orders.add(order.isAscending()
                        ? cb.asc(root.get(order.getProperty()))
                        : cb.desc(root.get(order.getProperty())));
            }
            query.orderBy(orders);
        }

        TypedQuery<InventoryReleaseEventEntity> typedQuery = entityManager.createQuery(query);
        typedQuery.setMaxResults(limit);
        return typedQuery.getResultList();
    }
}

