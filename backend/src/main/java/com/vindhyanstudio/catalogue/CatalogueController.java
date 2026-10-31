/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.catalogue;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.*;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.storage.StorageService;

@RestController
@RequestMapping("/api/catalogue")
public class CatalogueController {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final StorageService storage;

    public CatalogueController(CategoryRepository categories, ProductRepository products, StorageService storage) {
        this.categories = categories;
        this.products = products;
        this.storage = storage;
    }

    public record CategoryDto(String name, String slug, long productCount, String coverImage) {}

    public record ProductCard(String sku, String name, String slug, String category, String categorySlug,
                              BigDecimal mrp, BigDecimal sellingPrice, int offerPct, int stock, String image,
                              boolean customisable) {}

    public record ProductDetail(String sku, String name, String slug, String category, String categorySlug,
                                String description, BigDecimal mrp, BigDecimal sellingPrice, int offerPct, int stock,
                                List<String> images, CustomisationSpec customisation, List<String> colours,
                                List<String> sizes, int leadTimeDays, boolean returnable) {}

    /** Browsers may reuse catalogue data for a minute (admin changes show up within ~60 s). */
    private static final org.springframework.http.CacheControl CACHE =
            org.springframework.http.CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePublic();

    @GetMapping("/categories")
    org.springframework.http.ResponseEntity<List<CategoryDto>> categories() {
        return org.springframework.http.ResponseEntity.ok().cacheControl(CACHE).body(categoryList());
    }

    private List<CategoryDto> categoryList() {
        Map<Long, List<Product>> byCat = products.search(null, null).stream()
                .collect(Collectors.groupingBy(p -> p.getCategory().getId()));
        return categories.findAllByOrderBySortOrderAsc().stream()
                .filter(c -> byCat.containsKey(c.getId()))
                .map(c -> {
                    List<Product> ps = byCat.get(c.getId());
                    return new CategoryDto(c.getName(), c.getSlug(), ps.size(), firstImage(ps.get(0)));
                })
                .toList();
    }

    @GetMapping("/products")
    org.springframework.http.ResponseEntity<List<ProductCard>> products(@RequestParam(required = false) String category,
                                                                        @RequestParam(required = false) String q) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(CACHE).body(productList(category, q));
    }

    private List<ProductCard> productList(String category, String q) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        String cat = category == null || category.isBlank() ? null : category;
        return products.search(cat, like).stream()
                .sorted(Comparator.comparing((Product p) -> p.getStock() <= 0))
                .map(this::card)
                .toList();
    }

    @GetMapping("/products/{slug}")
    org.springframework.http.ResponseEntity<ProductDetail> product(@PathVariable String slug) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(CACHE).body(productDetail(slug));
    }

    private ProductDetail productDetail(String slug) {
        Product p = products.findBySlugAndActiveTrue(slug).orElseThrow(() -> ApiException.notFound("Product not found"));
        Category c = p.getCategory();
        return new ProductDetail(p.getSku(), p.getName(), p.getSlug(), c.getName(), c.getSlug(), p.getDescription(),
                p.getMrp(), p.getSellingPrice(), p.getOfferPct(), p.getStock(),
                p.getImages().stream().map(i -> storage.publicUrl(i.getStorageKey())).toList(),
                p.spec(), c.colourList(), c.sizeList(), c.getLeadTimeDays(), c.isReturnable());
    }

    ProductCard card(Product p) {
        return new ProductCard(p.getSku(), p.getName(), p.getSlug(), p.getCategory().getName(), p.getCategory().getSlug(),
                p.getMrp(), p.getSellingPrice(), p.getOfferPct(), p.getStock(), firstImage(p),
                p.spec().madeToOrder() || p.spec().colour() || p.spec().size());
    }

    private String firstImage(Product p) {
        return p.getImages().isEmpty() ? null : storage.publicUrl(p.getImages().get(0).getStorageKey());
    }
}
