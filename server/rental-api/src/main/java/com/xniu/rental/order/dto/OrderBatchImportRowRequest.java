package com.xniu.rental.order.dto;

public record OrderBatchImportRowRequest(
    Integer lineNo,
    String customerName,
    String customerPhone,
    String userAccountId,
    String storeSkuCode,
    String packageCode,
    String verificationAmount,
    String frameSerialNo,
    String batterySerialNo,
    String orderedAt,
    String expectedPickupAt
) {
}
