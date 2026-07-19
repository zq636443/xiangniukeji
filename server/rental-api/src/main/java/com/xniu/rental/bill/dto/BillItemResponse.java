package com.xniu.rental.bill.dto;

import java.math.BigDecimal;

public record BillItemResponse(
    Long id,
    String itemType,
    String itemName,
    BigDecimal amount
) {
}
