package com.xniu.rental.asset.dto;

public record AssetTypeResponse(
    Long id,
    String typeCode,
    String typeName,
    String assetClass,
    String serialLabel,
    boolean systemDefined,
    Integer sortOrder,
    String status,
    int assetCount
) {
}
