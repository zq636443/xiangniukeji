package com.xniu.rental.overdue.dto;

import java.time.LocalDateTime;

public record OverdueCollectionLogResponse(
    Long id,
    Long overdueCaseId,
    String collectionStatus,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
