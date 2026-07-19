package com.xniu.rental.pay.dto;

public record AgreementSignResponse(
    AgreementResponse agreement,
    String signUrl
) {
}
