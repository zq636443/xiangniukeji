package com.xniu.rental.pay.dto;

public record FundAuthCreateResponse(
    FundAuthResponse authorization,
    String orderStr
) {
}
