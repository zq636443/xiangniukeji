package com.xniu.rental.ops.dto;

import java.time.LocalDateTime;

public record ExportTaskResponse(
    Long id,
    String taskNo,
    String exportType,
    String requestParams,
    String taskStatus,
    String fileUrl,
    String failureReason,
    Long createdBy,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {
}
