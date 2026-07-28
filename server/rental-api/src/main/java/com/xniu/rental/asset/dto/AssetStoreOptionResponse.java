package com.xniu.rental.asset.dto;

public record AssetStoreOptionResponse(
    Long id,
    Long merchantId,
    String storeCode,
    String storeName
) {
}
