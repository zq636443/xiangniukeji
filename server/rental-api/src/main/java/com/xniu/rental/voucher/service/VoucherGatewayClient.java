package com.xniu.rental.voucher.service;

import com.xniu.rental.common.BusinessException;
import com.xniu.rental.voucher.config.VoucherGatewayProperties;
import com.xniu.rental.voucher.model.SourcePlatform;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(VoucherGatewayProperties.class)
public class VoucherGatewayClient {

    private final VoucherGatewayProperties properties;

    public VoucherGatewayClient(VoucherGatewayProperties properties) {
        this.properties = properties;
    }

    public GatewayResult prepare(SourcePlatform platform, String voucherCode, BigDecimal voucherAmount) {
        if (!properties.useMock()) {
            ensureConfigured(platform, true);
        }
        return mockResult("PREPARE", voucherCode, voucherAmount);
    }

    public GatewayResult verify(SourcePlatform platform, String voucherCode, BigDecimal voucherAmount) {
        if (!properties.useMock()) {
            ensureConfigured(platform, false);
        }
        return mockResult("VERIFY", voucherCode, voucherAmount);
    }

    public GatewayResult consume(SourcePlatform platform, String voucherCode, BigDecimal voucherAmount) {
        if (!properties.useMock()) {
            ensureConfigured(platform, false);
        }
        return mockResult("CONSUME", voucherCode, voucherAmount);
    }

    private void ensureConfigured(SourcePlatform platform, boolean prepare) {
        var url = switch (platform) {
            case DOUYIN -> prepare ? properties.douyinPrepareUrl() : properties.douyinVerifyUrl();
            case MEITUAN -> prepare ? properties.meituanPrepareUrl() : properties.meituanConsumeUrl();
            case XIANYU -> null;
        };
        if (url == null || url.isBlank()) {
            if (platform == SourcePlatform.XIANYU) {
                return;
            }
            throw BusinessException.badRequest("未配置" + platform + "核销接口地址，不能执行自动核销");
        }
    }

    private GatewayResult mockResult(String action, String voucherCode, BigDecimal voucherAmount) {
        return new GatewayResult(
            true,
            action + "-" + UUID.randomUUID().toString().substring(0, 8),
            "平台核销码 " + voucherCode,
            voucherAmount == null ? BigDecimal.ZERO : voucherAmount,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusMonths(1),
            "{\"mode\":\"MOCK\",\"action\":\"" + action + "\",\"voucherCode\":\"" + voucherCode + "\"}",
            null
        );
    }

    public record GatewayResult(
        boolean success,
        String externalId,
        String voucherTitle,
        BigDecimal voucherAmount,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String rawPayload,
        String failureReason
    ) {
    }
}
