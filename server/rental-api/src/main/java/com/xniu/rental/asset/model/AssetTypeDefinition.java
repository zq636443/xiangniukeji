package com.xniu.rental.asset.model;

import java.time.LocalDateTime;

public record AssetTypeDefinition(
    Long id,
    String typeCode,
    String typeName,
    AssetType assetClass,
    String serialLabel,
    boolean systemDefined,
    Integer sortOrder,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public boolean enabled() {
        return "ENABLED".equals(status);
    }
}
