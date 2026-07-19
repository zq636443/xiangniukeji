package com.xniu.rental.contract.model;

import java.time.LocalDateTime;

public record ContractNotify(
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
