package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SystemAccountCreateRequest(
    @NotBlank(message = "请选择角色") String roleCode,
    @NotBlank(message = "请输入登录账号") String username,
    @NotBlank(message = "请输入显示名称") String displayName,
    @NotBlank(message = "请输入手机号") String phone,
    @NotBlank(message = "请输入初始密码") String password,
    Long merchantId,
    Long investorId,
    List<Long> storeIds
) {
}
