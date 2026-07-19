package com.xniu.rental.pay.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundAuthOrder(
    Long id,
    String authOrderNo,
    Long orderId,
    Long userAccountId,
    String alipayUserId,
    Long merchantId,
    Long storeId,
    FundAuthType authType,
    FundAuthStatus authStatus,
    BigDecimal authAmount,
    BigDecimal frozenAmount,
    BigDecimal capturedAmount,
    BigDecimal releasedAmount,
    String outRequestNo,
    String alipayAuthNo,
    String alipayOperationId,
    String orderStr,
    String subject,
    String lastError,
    LocalDateTime authorizedAt,
    LocalDateTime closedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
