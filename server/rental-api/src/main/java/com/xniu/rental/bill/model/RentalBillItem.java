package com.xniu.rental.bill.model;

import java.math.BigDecimal;

public record RentalBillItem(
    Long id,
    Long billId,
    BillItemType itemType,
    String itemName,
    BigDecimal amount
) {
}
