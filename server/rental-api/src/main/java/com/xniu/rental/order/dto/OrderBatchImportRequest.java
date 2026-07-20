package com.xniu.rental.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderBatchImportRequest(
    @NotEmpty(message = "请至少传入一条订单数据")
    @Size(max = 500, message = "单次最多导入500条订单")
    List<@Valid OrderBatchImportRowRequest> rows
) {
}
