package com.xniu.rental.asset.dto;

public record AssetReturnRequest(
    Long returnStoreId,
    String frameResultStatus,
    String batteryResultStatus,
    String remark
) {
}
