package com.xniu.rental.contract.dto;

import java.time.LocalDateTime;

public record ContractTemplateResponse(
    Long id,
    String templateCode,
    String templateName,
    String contractType,
    String versionNo,
    String providerTemplateId,
    String content,
    String status,
    String remark,
    LocalDateTime createdAt
) {
}
