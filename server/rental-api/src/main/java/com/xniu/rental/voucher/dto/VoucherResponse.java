package com.xniu.rental.voucher.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherResponse(
    Long id,
    String sourcePlatform,
    String voucherCode,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    Long packageId,
    Long orderId,
    Long signFeeBillId,
    String verifyStatus,
    String voucherTitle,
    BigDecimal voucherAmount,
    BigDecimal signFeeAmount,
    String externalPrepareId,
    String externalVerifyId,
    String externalConsumeId,
    LocalDateTime validFrom,
    LocalDateTime validTo,
    Integer retryCount,
    String failureReason,
    LocalDateTime verifiedAt,
    LocalDateTime consumedAt,
    String exceptionReason,
    LocalDateTime createdAt
) {
}
