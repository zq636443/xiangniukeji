package com.xniu.rental.asset.dto;

import java.util.List;

public record AssetDetailResponse(
    AssetResponse asset,
    List<AssetRentalRecordResponse> rentals,
    List<AssetMaintenanceResponse> maintenances
) {
}
