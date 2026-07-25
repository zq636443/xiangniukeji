package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.investor.dto.InvestorRequest;
import com.xniu.rental.investor.service.InvestorService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
class InvestorFeeIntegrationTests {

    @Autowired
    private InvestorService investorService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setCurrentAccount() {
        AuthContext.set(new CurrentAccount(
            "investor-fee-test-token",
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
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void createAndUpdateShouldPersistConfiguredOperationFeeRate() {
        var created = investorService.createInvestor(request("0.0800"));

        assertThat(created.operationFeeRate()).isEqualByComparingTo("0.0800");
        assertThat(rateInDatabase(created.id())).isEqualByComparingTo("0.0800");

        var updated = investorService.updateInvestor(created.id(), request("0.1250"));

        assertThat(updated.operationFeeRate()).isEqualByComparingTo("0.1250");
        assertThat(rateInDatabase(created.id())).isEqualByComparingTo("0.1250");
    }

    @Test
    void operationFeeRateMustStayBetweenZeroAndOne() {
        assertThatThrownBy(() -> investorService.createInvestor(request("-0.0001")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("0 到 1");
        assertThatThrownBy(() -> investorService.createInvestor(request("1.0001")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("0 到 1");
    }

    private InvestorRequest request(String operationFeeRate) {
        var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return new InvestorRequest(
            "出资方-" + suffix,
            "测试联系人",
            "138" + suffix,
            new BigDecimal(operationFeeRate),
            false,
            null,
            null,
            null,
            null
        );
    }

    private BigDecimal rateInDatabase(Long investorId) {
        return jdbcTemplate.queryForObject(
            "SELECT operation_fee_rate FROM investor WHERE id = ?",
            BigDecimal.class,
            investorId
        );
    }
}
