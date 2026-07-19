package com.xniu.rental.product.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
    @NotBlank(message = "请输入分类名称") String categoryName,
    Integer sortOrder
) {
}
