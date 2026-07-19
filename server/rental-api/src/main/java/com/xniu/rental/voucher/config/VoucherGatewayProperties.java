package com.xniu.rental.voucher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xniu.voucher")
public record VoucherGatewayProperties(
    Boolean mockEnabled,
    String douyinPrepareUrl,
    String douyinVerifyUrl,
    String douyinQueryUrl,
    String meituanPrepareUrl,
    String meituanConsumeUrl,
    String meituanQueryUrl
) {
    public boolean useMock() {
        return !Boolean.FALSE.equals(mockEnabled);
    }
}
