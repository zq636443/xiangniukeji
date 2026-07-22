package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssetTypeRequest(
    @NotBlank(message = "请输入类型名称")
    @Size(max = 96, message = "类型名称不能超过96个字符")
    String typeName,
    @NotBlank(message = "请选择业务归类") String assetClass,
    @NotBlank(message = "请输入编号字段名称")
    @Size(max = 64, message = "编号字段名称不能超过64个字符")
    String serialLabel,
    Integer sortOrder,
    String status
) {
}
