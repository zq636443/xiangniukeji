package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.settlement.dto.ProfitRuleRequest;
import com.xniu.rental.settlement.dto.SettlementPreviewRequest;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.model.SettlementRuleStatus;
import com.xniu.rental.settlement.service.SettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class SettlementProfitRuleManagementIntegrationTests {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setPlatformAccount() {
        AuthContext.set(platformAccount());
        settlementService.listStoreRules();
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void platformCanCreateEditToggleAndDeleteStoreChannelRule() {
        var created = settlementService.createRule(ruleRequest(
            "演示门店抖音专项规则",
            "DOUYIN",
            100,
            new BigDecimal("0.04"),
            new BigDecimal("0.02"),
            new BigDecimal("0.20"),
            new BigDecimal("0.10"),
            new BigDecimal("0.15"),
            new BigDecimal("0.55")
        ));

        assertThat(created.storeId()).isEqualTo(1L);
        assertThat(created.merchantId()).isEqualTo(1L);
        assertThat(created.sourceChannel()).isEqualTo("DOUYIN");
        assertThat(created.status()).isEqualTo("ENABLED");

        var douyinPreview = settlementService.preview(new SettlementPreviewRequest(
            1L,
            null,
            null,
            new BigDecimal("1000.00"),
            "DOUYIN"
        ));
        assertThat(douyinPreview.matchedRuleId()).isEqualTo(created.id());
        assertThat(douyinPreview.channelFeeAmount()).isEqualByComparingTo("40.00");
        assertThat(douyinPreview.platformFeeAmount()).isEqualByComparingTo("20.00");

        var updated = settlementService.updateRule(created.id(), ruleRequest(
            "演示门店美团专项规则",
            "MEITUAN",
            120,
            new BigDecimal("0.06"),
            new BigDecimal("0.02"),
            new BigDecimal("0.18"),
            new BigDecimal("0.12"),
            new BigDecimal("0.15"),
            new BigDecimal("0.55")
        ));
        assertThat(updated.ruleName()).isEqualTo("演示门店美团专项规则");
        assertThat(updated.sourceChannel()).isEqualTo("MEITUAN");
        assertThat(updated.priority()).isEqualTo(120);

        var meituanPreview = settlementService.preview(new SettlementPreviewRequest(
            1L,
            null,
            null,
            new BigDecimal("1000.00"),
            "MEITUAN"
        ));
        assertThat(meituanPreview.matchedRuleId()).isEqualTo(created.id());
        assertThat(meituanPreview.channelFeeAmount()).isEqualByComparingTo("60.00");

        var disabled = settlementService.updateRuleStatus(created.id(), SettlementRuleStatus.DISABLED);
        assertThat(disabled.status()).isEqualTo("DISABLED");
        assertThat(settlementService.preview(new SettlementPreviewRequest(
            1L,
            null,
            null,
            new BigDecimal("1000.00"),
            "MEITUAN"
        )).matchedRuleId()).isNotEqualTo(created.id());

        settlementService.updateRuleStatus(created.id(), SettlementRuleStatus.ENABLED);
        settlementService.deleteRule(created.id());
        assertThat(settlementService.listRules("STORE", null))
            .noneMatch(rule -> rule.id().equals(created.id()));
    }

    @Test
    void takeawayPreviewShouldUseTheEnteredPeriodBatteryCost() {
        jdbcTemplate.update("""
            UPDATE product_sku
            SET battery_cost_daily_amount = 6.80,
                battery_cost_monthly_amount = 200.00
            WHERE id = 2
            """);

        var preview = settlementService.preview(new SettlementPreviewRequest(
            2L,
            null,
            null,
            new BigDecimal("399.00"),
            "DIRECT",
            new BigDecimal("200.00")
        ));

        assertThat(preview.calculationVersion()).isEqualTo("PROFIT_V3");
        assertThat(preview.batteryCostAmount()).isEqualByComparingTo("200.00");
        assertThat(preview.channelReferralAmount()).isEqualByComparingTo("79.80");
        assertThat(preview.distributableAmount()).isEqualByComparingTo("87.28");
        assertThat(preview.storeOperationAmount()).isEqualByComparingTo("16.37");
        assertThat(preview.maintenanceFundAmount()).isEqualByComparingTo("10.91");
        assertThat(preview.investorShareAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void defaultAndSnapshottedRulesCannotBeRemoved() {
        var defaultRule = settlementService.listStoreRules().stream()
            .filter(rule -> rule.storeId().equals(1L))
            .filter(rule -> rule.sourceChannel() == null)
            .filter(rule -> "ENABLED".equals(rule.status()))
            .findFirst()
            .orElseThrow();

        assertThatThrownBy(() -> settlementService.updateRuleStatus(defaultRule.id(), SettlementRuleStatus.DISABLED))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必须保留一条当前生效的全部渠道默认规则");
        assertThatThrownBy(() -> settlementService.deleteRule(defaultRule.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必须保留一条当前生效的全部渠道默认规则");
        assertThatThrownBy(() -> settlementService.updateRule(defaultRule.id(), ruleRequest(
            "演示门店抖音规则",
            "DOUYIN",
            100,
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必须保留一条当前生效的全部渠道默认规则");

        var channelRule = settlementService.createRule(ruleRequest(
            "演示门店抖音快照规则",
            "DOUYIN",
            200,
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        ));
        var snapshot = settlementService.createSnapshot(new SnapshotCreateRequest(
            "PREVIEW",
            null,
            1L,
            null,
            null,
            new BigDecimal("199.00"),
            "DOUYIN"
        ));
        assertThat(snapshot.matchedRuleId()).isEqualTo(channelRule.id());
        assertThatThrownBy(() -> settlementService.deleteRule(channelRule.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已生成分润快照");
    }

    @Test
    void merchantCannotManageProfitRulesEvenWithWritePermission() {
        AuthContext.set(new CurrentAccount(
            "merchant-token",
            new CurrentAccountResponse(
                2L,
                "MERCHANT_OWNER",
                "merchant-owner",
                "18800000002",
                null,
                "Merchant Owner",
                1L,
                null,
                null,
                List.of("MERCHANT_OWNER"),
                List.of("settlement.read", "settlement.write"),
                List.of()
            )
        ));

        assertThatThrownBy(() -> settlementService.createRule(ruleRequest(
            "越权规则",
            "DOUYIN",
            100,
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("平台账号");
    }

    private ProfitRuleRequest ruleRequest(
        String name,
        String sourceChannel,
        int priority,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate
    ) {
        return new ProfitRuleRequest(
            name,
            "STORE",
            sourceChannel,
            priority,
            null,
            null,
            1L,
            null,
            channelFeeRate,
            platformFeeRate,
            storeOperationRate,
            maintenanceFundRate,
            channelReferralRate,
            investorShareRate,
            LocalDateTime.now().minusMinutes(1),
            null
        );
    }

    private CurrentAccount platformAccount() {
        return new CurrentAccount(
            "test-token",
            new CurrentAccountResponse(
                1L,
                "PLATFORM_ADMIN",
                "admin",
                "18800000001",
                null,
                "Platform Admin",
                null,
                null,
                null,
                List.of("PLATFORM_ADMIN"),
                List.of("system.admin"),
                List.of()
            )
        );
    }
}
