package com.xniu.rental.asset.dto;

public record AssetBatchImportRowRequest(
    Integer lineNo,
    String assetType,
    String serialNo,
    String investorCode,
    String storeCode,
    String purchaseAmount,
    String maintenanceFeeAmount,
    String residualValue,
    String purchasedAt,
    String arrivalBatchNo
) {
    public AssetBatchImportRowRequest(
        Integer lineNo,
        String assetType,
        String serialNo,
        String investorCode,
        String storeCode,
        String purchaseAmount,
        String maintenanceFeeAmount,
        String residualValue,
        String purchasedAt
    ) {
        this(lineNo, assetType, serialNo, investorCode, storeCode, purchaseAmount, maintenanceFeeAmount, residualValue, purchasedAt, null);
    }
}
