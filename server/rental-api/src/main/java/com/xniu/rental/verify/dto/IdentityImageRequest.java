package com.xniu.rental.verify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IdentityImageRequest(
    @NotNull(message = "请选择订单") Long orderId,
    @NotBlank(message = "请上传身份证正面") String frontImageUrl,
    @NotBlank(message = "请上传身份证反面") String backImageUrl
) {
}
