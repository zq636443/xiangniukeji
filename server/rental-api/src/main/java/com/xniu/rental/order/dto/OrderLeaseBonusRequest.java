package com.xniu.rental.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderLeaseBonusRequest(
    @NotBlank(message = "请选择赠送类型") String bonusType,
    @NotNull(message = "请输入赠送天数")
    @Min(value = 1, message = "赠送天数至少为 1 天")
    @Max(value = 999, message = "单次赠送天数不能超过 999 天")
    Integer bonusDays,
    @Size(max = 255, message = "赠送备注不能超过 255 个字") String remark
) {
}
