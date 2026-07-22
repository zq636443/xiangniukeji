package com.xniu.rental.externalorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalRentalOrderCreateRequest(
    @NotBlank(message = "请选择订单来源平台") String sourcePlatform,
    String externalOrderNo,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    @NotNull(message = "请选择 SKU") Long packageId,
    @NotBlank(message = "请输入客户姓名") String customerName,
    @NotBlank(message = "请输入客户手机号") String customerPhone,
    @NotNull(message = "请选择起租时间") LocalDateTime rentStartedAt,
    LocalDateTime expectedReturnAt,
    Long frameAssetId,
    Long batteryAssetId,
    BigDecimal externalRentalAmount,
    @DecimalMin(value = "0.00", message = "实际核销金额不能小于 0")
    @Digits(integer = 10, fraction = 2, message = "实际核销金额最多保留 2 位小数")
    BigDecimal verificationAmount,
    BigDecimal signFeeAmount,
    BigDecimal depositAmount,
    String remark
) {
}
