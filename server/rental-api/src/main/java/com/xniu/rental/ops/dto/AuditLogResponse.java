package com.xniu.rental.ops.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
    Long id,
    Long accountId,
    String accountType,
    String requestMethod,
    String requestUri,
    String queryString,
    Integer httpStatus,
    Boolean success,
    String errorMessage,
    String clientIp,
    String userAgent,
    LocalDateTime createdAt
) {
}
