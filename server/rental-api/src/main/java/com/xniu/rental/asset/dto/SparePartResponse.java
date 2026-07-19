package com.xniu.rental.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SparePartResponse(
    Long id,
    String partCode,
    String partName,
    String spec,
    String unit,
    BigDecimal procurementPrice,
    BigDecimal unitPrice,
    BigDecimal buybackPrice,
    Integer stockQuantity,
    BigDecimal stockAmount,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
