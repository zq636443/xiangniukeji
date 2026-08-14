package com.xniu.rental.asset.dto;

public record AssetTransferStoreResponse(
    Long id,
    Long merchantId,
    String storeCode,
    String storeName
) {
}
