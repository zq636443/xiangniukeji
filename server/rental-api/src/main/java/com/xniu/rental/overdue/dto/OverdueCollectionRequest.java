package com.xniu.rental.overdue.dto;

import jakarta.validation.constraints.NotBlank;

public record OverdueCollectionRequest(
    @NotBlank(message = "请选择催缴状态") String collectionStatus,
    String remark
) {
}
