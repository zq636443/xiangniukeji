package com.xniu.rental.product.dto;

public record CategoryResponse(
    Long id,
    String categoryCode,
    String categoryName,
    Integer sortOrder,
    String status
) {
}
