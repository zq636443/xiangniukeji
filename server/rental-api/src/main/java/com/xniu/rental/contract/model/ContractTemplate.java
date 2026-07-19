package com.xniu.rental.contract.model;

import java.time.LocalDateTime;

public record ContractTemplate(
    Long id,
    String templateCode,
    String templateName,
    ContractType contractType,
    String versionNo,
    String providerTemplateId,
    String content,
    ContractTemplateStatus status,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
