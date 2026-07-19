package com.xniu.rental.externalorder.dto;

public record ExternalRentalOrderCompleteRequest(
    Long returnStoreId,
    String frameResultStatus,
    String batteryResultStatus,
    String remark
) {
}
