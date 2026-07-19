package com.xniu.rental.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record EmployeeRequest(
    @NotNull(message = "请选择商户") Long merchantId,
    Long storeId,
    @NotBlank(message = "请输入账号") String username,
    @NotBlank(message = "请输入姓名") String displayName,
    @NotBlank(message = "请输入手机号") String phone,
    @NotBlank(message = "请输入初始密码") String password,
    @NotBlank(message = "请选择角色") String roleCode,
    List<Long> storeIds
) {
}
