package com.xniu.rental.order.dto;

public record OrderBatchImportRowResultResponse(
    Integer lineNo,
    boolean success,
    Long orderId,
    String orderNo,
    String customerName,
    String customerPhone,
    String message
) {
}
