package com.xniu.rental.bill.model;

public enum BillOperationType {
    GENERATE,
    CANCEL,
    MARK_OVERDUE,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED;

    public static BillOperationType fromDb(String value) {
        return switch (value) {
            case "PAY_SUCCESS" -> PAYMENT_SUCCESS;
            case "PAY_FAILED", "PAY_FAIL" -> PAYMENT_FAILED;
            default -> BillOperationType.valueOf(value);
        };
    }
}
