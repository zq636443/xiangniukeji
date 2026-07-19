package com.xniu.rental.verify.dto;

import java.time.LocalDateTime;

public record IdentityVerificationResponse(
    Long id,
    Long userAccountId,
    Long orderId,
    String frontImageUrl,
    String backImageUrl,
    String ocrStatus,
    String realNameStatus,
    String realNameMasked,
    String idNoMasked,
    String gender,
    String birthDate,
    String addressMasked,
    String ocrProvider,
    String certifyProvider,
    String externalCertifyId,
    String failureReason,
    LocalDateTime verifiedAt,
    LocalDateTime createdAt
) {
}
