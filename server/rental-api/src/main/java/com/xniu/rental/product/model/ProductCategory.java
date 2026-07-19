package com.xniu.rental.product.model;

public record ProductCategory(
    Long id,
    String categoryCode,
    String categoryName,
    Integer sortOrder,
    ProductStatus status
) {
}
