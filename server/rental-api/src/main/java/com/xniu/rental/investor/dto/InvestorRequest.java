package com.xniu.rental.investor.dto;

import jakarta.validation.constraints.NotBlank;

public record InvestorRequest(
    @NotBlank(message = "请输入出资方名称") String investorName,
    @NotBlank(message = "请输入联系人") String contactName,
    @NotBlank(message = "请输入联系电话") String contactPhone,
    Boolean createAccount,
    String username,
    String displayName,
    String phone,
    String password
) {
}
