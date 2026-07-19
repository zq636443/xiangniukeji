package com.xniu.rental.overdue.model;

import java.time.LocalDateTime;

public record OverdueCollectionLog(
    Long id,
    Long overdueCaseId,
    CollectionStatus collectionStatus,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
