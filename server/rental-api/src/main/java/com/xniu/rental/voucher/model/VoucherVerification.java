package com.xniu.rental.voucher.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherVerification(
    Long id,
    SourcePlatform sourcePlatform,
    String voucherCode,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    Long packageId,
    Long orderId,
    Long signFeeBillId,
    VoucherVerifyStatus verifyStatus,
    String voucherTitle,
    BigDecimal voucherAmount,
    BigDecimal verificationAmount,
    BigDecimal signFeeAmount,
    String externalPrepareId,
    String externalVerifyId,
    String externalConsumeId,
    LocalDateTime validFrom,
    LocalDateTime validTo,
    Integer retryCount,
    String rawPayload,
    String failureReason,
    LocalDateTime verifiedAt,
    LocalDateTime consumedAt,
    String exceptionReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
