package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.dto.StoreRequest;
import com.xniu.rental.merchant.service.MerchantService;
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
class MerchantStoreDeletionIntegrationTests {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setCurrentAccount() {
        AuthContext.set(new CurrentAccount(
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
                List.of("store.write", "store.read", "merchant.write", "merchant.read", "system.admin"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void emptyStoreCanBeDeleted() {
        var created = merchantService.createStore(new StoreRequest(
            1L,
            "可删除测试门店",
            "深圳市南山区删除路 9 号",
            "09:00-21:00",
            null,
            null
        ));

        merchantService.deleteStore(created.id());

        var count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM merchant_store WHERE id = ?", Integer.class, created.id());
        assertThat(count).isZero();
    }

    @Test
    void storeWithBusinessDataCannotBeDeleted() {
        assertThatThrownBy(() -> merchantService.deleteStore(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("暂不可删除")
            .hasMessageContaining("员工账号")
            .hasMessageContaining("门店商品")
            .hasMessageContaining("租赁订单");
    }
}
