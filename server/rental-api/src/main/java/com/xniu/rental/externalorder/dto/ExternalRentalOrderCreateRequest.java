package com.xniu.rental.externalorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalRentalOrderCreateRequest(
    @NotBlank(message = "请选择订单来源平台") String sourcePlatform,
    String externalOrderNo,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    @NotNull(message = "请选择 SKU") Long packageId,
    @Min(value = 1, message = "租期倍数不能小于 1")
    @Max(value = 120, message = "租期倍数不能大于 120")
    Integer leaseMultiplier,
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
    public ExternalRentalOrderCreateRequest(
        String sourcePlatform,
        String externalOrderNo,
        Long storeSkuId,
        Long packageId,
        String customerName,
        String customerPhone,
        LocalDateTime rentStartedAt,
        LocalDateTime expectedReturnAt,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal externalRentalAmount,
        BigDecimal verificationAmount,
        BigDecimal signFeeAmount,
        BigDecimal depositAmount,
        String remark
    ) {
        this(sourcePlatform, externalOrderNo, storeSkuId, packageId, null, customerName, customerPhone, rentStartedAt,
            expectedReturnAt, frameAssetId, batteryAssetId, externalRentalAmount, verificationAmount, signFeeAmount,
            depositAmount, remark);
    }
}
