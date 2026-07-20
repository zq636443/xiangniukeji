package com.xniu.rental.asset.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AssetBatchImportRequest(
    @NotEmpty(message = "请至少传入一条资产数据")
    @Size(max = 500, message = "单次最多导入500条资产")
    List<@Valid AssetBatchImportRowRequest> rows
) {
}
