package com.xniu.rental.merchant.dto;

import jakarta.validation.constraints.NotBlank;

public record MerchantRequest(
    @NotBlank(message = "请输入商户名称") String merchantName,
    @NotBlank(message = "请输入联系人") String contactName,
    @NotBlank(message = "请输入联系电话") String contactPhone,
    String businessLicenseNo,
    Boolean createOwnerAccount,
    String ownerUsername,
    String ownerDisplayName,
    String ownerPhone,
    String ownerPassword
) {
}
