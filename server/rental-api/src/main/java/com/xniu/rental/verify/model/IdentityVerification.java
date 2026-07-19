package com.xniu.rental.verify.model;

import java.time.LocalDateTime;

public record IdentityVerification(
    Long id,
    Long userAccountId,
    Long orderId,
    String frontImageUrl,
    String backImageUrl,
    OcrStatus ocrStatus,
    RealNameStatus realNameStatus,
    String realNameMasked,
    String idNoMasked,
    String idNoHash,
    String gender,
    String birthDate,
    String addressMasked,
    String ocrProvider,
    String certifyProvider,
    String externalCertifyId,
    String failureReason,
    LocalDateTime verifiedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
