package com.xniu.rental.asset.dto;

import java.math.BigDecimal;

public record AssetMaintenancePartResponse(
    Long id,
    Long maintenanceId,
    Long partId,
    String partNameSnapshot,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal totalAmount,
    String remark
) {
}
