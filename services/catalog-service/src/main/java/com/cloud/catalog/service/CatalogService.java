package com.cloud.catalog.service;

import com.cloud.catalog.domain.Product;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CatalogService {

    private final Map<String, Product> productsBySku = new ConcurrentHashMap<>();

    public Product upsertProduct(String skuId, String name, String description, BigDecimal price, boolean active) {
        Product product = new Product(
                skuId,
                name,
                description == null ? "" : description,
                price,
                active,
                Instant.now()
        );
        productsBySku.put(skuId, product);
        return product;
    }

    public Product getProduct(String skuId) {
        Product product = productsBySku.get(skuId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found for skuId=" + skuId);
        }
        return product;
    }

    public List<Product> listProducts(Boolean active) {
        return productsBySku.values().stream()
                .filter(product -> active == null || product.active() == active)
                .sorted(Comparator.comparing(Product::skuId))
                .toList();
    }
}
