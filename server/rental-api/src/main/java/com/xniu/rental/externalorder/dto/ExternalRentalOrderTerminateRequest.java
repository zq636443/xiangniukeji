package com.xniu.rental.externalorder.dto;

import jakarta.validation.constraints.NotBlank;

public record ExternalRentalOrderTerminateRequest(
    Long returnStoreId,
    String frameResultStatus,
    String batteryResultStatus,
    @NotBlank(message = "请输入提前终止原因") String terminationReason,
    String remark
) {
}
