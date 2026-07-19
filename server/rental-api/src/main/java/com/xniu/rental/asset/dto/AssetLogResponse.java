package com.xniu.rental.asset.dto;

import java.time.LocalDateTime;

public record AssetLogResponse(
    Long id,
    Long assetId,
    String logType,
    String fromValue,
    String toValue,
    String remark,
    LocalDateTime createdAt
) {
}
