package com.xniu.rental.contract.dto;

import java.time.LocalDateTime;

public record ContractResponse(
    Long id,
    String contractNo,
    Long orderId,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    Long templateId,
    String contractType,
    String contractStatus,
    String provider,
    String externalFlowId,
    String signUrl,
    String archivePdfUrl,
    String renderedContent,
    String failureReason,
    LocalDateTime sentAt,
    LocalDateTime signedAt,
    LocalDateTime archivedAt,
    LocalDateTime createdAt
) {
}
