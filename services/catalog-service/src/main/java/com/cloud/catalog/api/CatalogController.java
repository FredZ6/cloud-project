package com.cloud.catalog.api;

import com.cloud.catalog.domain.Product;
import com.cloud.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catalog", description = "Catalog product APIs")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PutMapping("/{skuId}")
    @Operation(summary = "Create or update catalog product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product upserted"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ProductResponse upsertProduct(@PathVariable("skuId") String skuId,
                                         @Valid @RequestBody UpsertProductRequest request) {
        Product product = catalogService.upsertProduct(
                skuId,
                request.name(),
                request.description(),
                request.price(),
                request.isActiveOrDefault()
        );
        return toResponse(product);
    }

    @GetMapping("/{skuId}")
    @Operation(summary = "Get product by SKU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ProductResponse getProduct(@PathVariable("skuId") String skuId) {
        return toResponse(catalogService.getProduct(skuId));
    }

    @GetMapping
    @Operation(summary = "List products")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products listed")
    })
    public List<ProductResponse> listProducts(@RequestParam(value = "active", required = false) Boolean active) {
        return catalogService.listProducts(active).stream()
                .map(this::toResponse)
                .toList();
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.skuId(),
                product.name(),
                product.description(),
                product.price(),
                product.active(),
                product.updatedAt()
        );
    }
}
