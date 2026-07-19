package com.xniu.rental.contract.model;

import java.time.LocalDateTime;

public record RentalContract(
    Long id,
    String contractNo,
    Long orderId,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    Long templateId,
    ContractType contractType,
    ContractStatus contractStatus,
    String provider,
    String externalFlowId,
    String signUrl,
    String archivePdfUrl,
    String renderedContent,
    String failureReason,
    LocalDateTime sentAt,
    LocalDateTime signedAt,
    LocalDateTime archivedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
