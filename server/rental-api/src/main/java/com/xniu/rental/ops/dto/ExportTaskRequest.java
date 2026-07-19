package com.xniu.rental.ops.dto;

import jakarta.validation.constraints.NotBlank;

public record ExportTaskRequest(
    @NotBlank(message = "请选择导出类型") String exportType,
    String requestParams
) {
}
