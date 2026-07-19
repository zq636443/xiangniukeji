package com.xniu.rental.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundAuthResponse(
    Long id,
    String authOrderNo,
    Long orderId,
    Long userAccountId,
    String alipayUserId,
    Long merchantId,
    Long storeId,
    String authType,
    String authStatus,
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
    LocalDateTime createdAt
) {
}
