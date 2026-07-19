package com.xniu.rental.externalorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ExternalRentalOrderBatchImportRequest(
    @NotEmpty(message = "请至少传入一条补录订单") List<@Valid ExternalRentalOrderImportRowRequest> rows
) {
}
