package com.xniu.rental.externalorder.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExternalOrderPricingFilterRequest(
    List<Long> orderIds,
    Long storeId,
    String status,
    String sourcePlatform,
    Long storeSkuId,
    Long packageId,
    LocalDateTime rentStartedFrom,
    LocalDateTime rentStartedTo,
    LocalDateTime expectedReturnFrom,
    LocalDateTime expectedReturnTo,
    String keyword
) {
}
