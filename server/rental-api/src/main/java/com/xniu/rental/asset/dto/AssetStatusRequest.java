package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record AssetStatusRequest(
    @NotBlank(message = "请选择资产状态") String status,
    String remark
) {
}
