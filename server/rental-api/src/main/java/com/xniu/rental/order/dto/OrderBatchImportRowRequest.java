package com.xniu.rental.order.dto;

public record OrderBatchImportRowRequest(
    Integer lineNo,
    String customerName,
    String customerPhone,
    String userAccountId,
    String storeSkuCode,
    String packageCode,
    String leaseMultiplier,
    String verificationAmount,
    String frameSerialNo,
    String batterySerialNo,
    String orderedAt,
    String expectedPickupAt
) {
    public OrderBatchImportRowRequest(
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
        this(lineNo, customerName, customerPhone, userAccountId, storeSkuCode, packageCode, null,
            verificationAmount, frameSerialNo, batterySerialNo, orderedAt, expectedPickupAt);
    }
}
