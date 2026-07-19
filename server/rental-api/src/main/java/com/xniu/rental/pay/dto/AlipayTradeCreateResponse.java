package com.xniu.rental.pay.dto;

public record AlipayTradeCreateResponse(
    PaymentResponse payment,
    String tradeNo
) {
}
