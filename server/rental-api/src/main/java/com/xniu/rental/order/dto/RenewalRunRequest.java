package com.xniu.rental.order.dto;

public record RenewalRunRequest(
    Integer limit,
    String remark
) {
}
