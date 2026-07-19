package com.xniu.rental.asset.dto;

public record AssetPickupRequest(
    Long frameAssetId,
    Long batteryAssetId,
    String remark
) {
}
