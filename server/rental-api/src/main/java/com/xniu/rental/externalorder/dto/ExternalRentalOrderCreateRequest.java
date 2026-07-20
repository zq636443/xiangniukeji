package com.xniu.rental.externalorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    BigDecimal signFeeAmount,
    BigDecimal depositAmount,
    String remark
) {
}
