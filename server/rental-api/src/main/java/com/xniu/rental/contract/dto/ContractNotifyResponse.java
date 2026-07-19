package com.xniu.rental.contract.dto;

import java.time.LocalDateTime;

public record ContractNotifyResponse(
    Long id,
    Long contractId,
    String externalFlowId,
    String notifyId,
    String contractStatus,
    Boolean verified,
    Boolean processed,
    String rawPayload,
    String failureReason,
    LocalDateTime receivedAt
) {
}
