package com.xniu.rental.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    String itemType,
    Long refId,
    String itemName,
    Integer quantity,
    BigDecimal unitAmount,
    BigDecimal totalAmount
) {
}
