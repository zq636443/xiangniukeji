package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotNull;

public record AssetInvestorChangeRequest(
    @NotNull(message = "请选择出资方") Long investorId,
    String remark
) {
}
