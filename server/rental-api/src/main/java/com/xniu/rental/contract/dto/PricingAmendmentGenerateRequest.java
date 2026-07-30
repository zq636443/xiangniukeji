package com.xniu.rental.contract.dto;

import jakarta.validation.constraints.NotNull;

public record PricingAmendmentGenerateRequest(
    @NotNull(message = "请选择续租调价记录") Long pricingRevisionId,
    Long templateId
) {
}
