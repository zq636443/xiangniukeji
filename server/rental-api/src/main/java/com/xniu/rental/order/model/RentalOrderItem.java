package com.xniu.rental.order.model;

import java.math.BigDecimal;

public record RentalOrderItem(
    Long id,
    Long orderId,
    OrderItemType itemType,
    Long refId,
    String itemName,
    Integer quantity,
    BigDecimal unitAmount,
    BigDecimal totalAmount
) {
}
