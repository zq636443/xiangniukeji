package com.xniu.rental.verify.dto;

import jakarta.validation.constraints.NotBlank;

public record RealNameConfirmRequest(
    @NotBlank(message = "请输入姓名") String realName,
    @NotBlank(message = "请输入身份证号") String idNo,
    String gender,
    String birthDate,
    String address
) {
}
