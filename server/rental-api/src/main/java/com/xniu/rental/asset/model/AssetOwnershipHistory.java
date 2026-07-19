package com.xniu.rental.asset.model;

import java.time.LocalDateTime;

public record AssetOwnershipHistory(
    Long id,
    Long assetId,
    Long investorId,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    String changeReason
) {
}
