package com.xniu.rental.externalorder.dto;

public record ExternalRentalOrderImportRowResultResponse(
    Integer lineNo,
    boolean success,
    Long orderId,
    String recordNo,
    String message
) {
}
