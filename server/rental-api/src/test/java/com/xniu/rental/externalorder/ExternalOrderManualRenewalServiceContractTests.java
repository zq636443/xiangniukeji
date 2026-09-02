package com.xniu.rental.externalorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderManualRenewalRequest;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalSource;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.externalorder.service.ExternalOrderManualRenewalService;
import com.xniu.rental.product.model.ProductSku;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.dto.SettlementSnapshotResponse;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalOrderManualRenewalServiceContractTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private ExternalRentalOrderRepository orderRepository;

    @Mock
    private ExternalOrderRenewalRepository renewalRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementService settlementService;

    @Mock
    private SettlementIncomeService settlementIncomeService;

    @Mock
    private SettlementStatementRepository settlementStatementRepository;

    @Mock
    private SettlementStatementService settlementStatementService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ExternalRentalOrder order;

    @Mock
    private SettlementRuleSnapshot sourceSnapshot;

    @Mock
    private ProductSku sku;

    @Mock
    private ExternalOrderRenewalEvent event;

    @Mock
    private SettlementSnapshotResponse eventSnapshot;

    @InjectMocks
    private ExternalOrderManualRenewalService service;

    @BeforeEach
    void setCurrentAccount() {
        AuthContext.set(new CurrentAccount(
            "manual-renewal-contract-token",
            new CurrentAccountResponse(
                7L,
                "PLATFORM_ADMIN",
                "manual-renewal-admin",
                "18800000007",
                null,
                "Manual Renewal Admin",
                null,
                null,
                null,
                List.of("PLATFORM_ADMIN"),
                List.of("order.operate"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void createsOneOffEventUnderTheOrderLockAndAdvancesOnlyThePaidThroughBoundary() {
        prepareActiveOrder(new BigDecimal("6.80"), new BigDecimal("200.00"));
        var end = START.plusDays(31);
        prepareCreatedEvent(end, new BigDecimal("397.30"), new BigDecimal("206.80"), "人工确认续租31天");

        var response = service.create(1L, new ExternalOrderManualRenewalRequest(
            START,
            end,
            new BigDecimal("397.30"),
            "人工确认续租31天"
        ));

        assertThat(response.renewalSource()).isEqualTo("MANUAL");
        assertThat(response.renewalAmount()).isEqualByComparingTo("397.30");
        assertThat(response.batteryCostAmount()).isEqualByComparingTo("206.80");

        var writes = inOrder(orderRepository, renewalRepository, settlementService, settlementIncomeService);
        writes.verify(orderRepository).findByIdForUpdate(1L);
        writes.verify(renewalRepository).nextPeriodNo(1L);
        writes.verify(renewalRepository).create(
            eq(1L),
            anyString(),
            eq(1),
            eq(START),
            eq(end),
            eq(new BigDecimal("397.30")),
            eq(new BigDecimal("129.00")),
            eq(new BigDecimal("206.80")),
            eq(ExternalOrderRenewalSource.MANUAL),
            eq(7L),
            eq("人工确认续租31天")
        );
        writes.verify(settlementService).createExternalRenewalSnapshot(
            42L,
            10L,
            new BigDecimal("397.30"),
            new BigDecimal("206.80")
        );
        writes.verify(settlementIncomeService).createExternalRenewalEntries(
            42L,
            "ERN-contract",
            99L,
            START,
            new BigDecimal("397.30")
        );
        writes.verify(orderRepository).advanceExpectedReturnAt(1L, end);
    }

    @Test
    void acceptsAnExactTwentyDayGrossWithoutChangingTheAutomaticSystemAmount() {
        prepareActiveOrder(null, null);
        var end = START.plusDays(20);
        prepareCreatedEvent(end, new BigDecimal("96.00"), BigDecimal.ZERO.setScale(2), "人工确认续租20天");

        var response = service.create(1L, new ExternalOrderManualRenewalRequest(
            START,
            end,
            new BigDecimal("96.00"),
            "人工确认续租20天"
        ));

        assertThat(response.periodStartAt()).isEqualTo(START);
        assertThat(response.periodEndAt()).isEqualTo(end);
        assertThat(response.renewalAmount()).isEqualByComparingTo("96.00");
        verify(renewalRepository).create(
            eq(1L), anyString(), eq(1), eq(START), eq(end),
            eq(new BigDecimal("96.00")), eq(new BigDecimal("129.00")), eq(BigDecimal.ZERO.setScale(2)),
            eq(ExternalOrderRenewalSource.MANUAL), eq(7L), eq("人工确认续租20天")
        );
        verify(orderRepository).advanceExpectedReturnAt(1L, end);
    }

    @Test
    void rejectsAnUnderfundedBatteryPeriodBeforeAnyFinancialOrBoundaryWrite() {
        prepareActiveOrder(new BigDecimal("6.80"), new BigDecimal("200.00"));

        assertThatThrownBy(() -> service.create(1L, new ExternalOrderManualRenewalRequest(
            START,
            START.plusDays(20),
            new BigDecimal("96.00"),
            "不足覆盖电池费"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不足以覆盖");

        verify(renewalRepository, never()).create(
            anyLong(), anyString(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(settlementService, never()).createExternalRenewalSnapshot(anyLong(), anyLong(), any(), any());
        verify(settlementIncomeService, never()).createExternalRenewalEntries(anyLong(), anyString(), anyLong(), any(), any());
        verify(orderRepository, never()).advanceExpectedReturnAt(anyLong(), any());
    }

    @Test
    void rejectsAStaleBoundaryAfterAnotherRenewalAdvancedTheOrder() {
        when(order.merchantId()).thenReturn(1L);
        when(order.storeId()).thenReturn(1L);
        when(order.orderStatus()).thenReturn(ExternalRentalOrderStatus.ACTIVE);
        when(order.expectedReturnAt()).thenReturn(START.plusDays(20));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(1L, new ExternalOrderManualRenewalRequest(
            START,
            START.plusDays(40),
            new BigDecimal("96.00"),
            "页面打开后订单已被推进"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("起点已变化");

        verify(orderRepository).findByIdForUpdate(1L);
        verify(renewalRepository, never()).create(
            anyLong(), anyString(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(orderRepository, never()).advanceExpectedReturnAt(anyLong(), any());
    }

    @Test
    void rejectsAManualRenewalWhoseOccurrenceMonthIsAlreadyLocked() {
        when(order.merchantId()).thenReturn(1L);
        when(order.storeId()).thenReturn(1L);
        when(order.orderStatus()).thenReturn(ExternalRentalOrderStatus.ACTIVE);
        when(order.expectedReturnAt()).thenReturn(START);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(settlementStatementRepository.hasLockedStatements("2026-08")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, new ExternalOrderManualRenewalRequest(
            START,
            START.plusDays(31),
            new BigDecimal("397.30"),
            "历史月已锁定"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("月份已锁定");

        verify(settlementStatementRepository).lockStatementsByMonthForUpdate("2026-08");
        verify(renewalRepository, never()).create(
            anyLong(), anyString(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(orderRepository, never()).advanceExpectedReturnAt(anyLong(), any());
    }

    @Test
    void rebuildsAnExistingDraftMonthInsideTheSameTransaction() {
        prepareActiveOrder(null, null);
        var end = START.plusDays(20);
        prepareCreatedEvent(end, new BigDecimal("96.00"), BigDecimal.ZERO.setScale(2), "草稿月续租");
        when(settlementStatementRepository.hasDraftStatements("2026-08")).thenReturn(true);

        service.create(1L, new ExternalOrderManualRenewalRequest(
            START,
            end,
            new BigDecimal("96.00"),
            "草稿月续租"
        ));

        verify(settlementStatementService).regenerateUnlockedMonthAlreadyLocked("2026-08");
    }

    private void prepareActiveOrder(BigDecimal dailyBatteryCost, BigDecimal monthlyBatteryCost) {
        when(order.id()).thenReturn(1L);
        lenient().when(order.recordNo()).thenReturn("EORD-contract");
        when(order.merchantId()).thenReturn(1L);
        when(order.storeId()).thenReturn(1L);
        when(order.skuId()).thenReturn(5L);
        when(order.orderStatus()).thenReturn(ExternalRentalOrderStatus.ACTIVE);
        when(order.expectedReturnAt()).thenReturn(START);
        when(order.settlementSnapshotId()).thenReturn(10L);
        lenient().when(order.renewalAmount()).thenReturn(new BigDecimal("129.00"));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        when(sourceSnapshot.sourceType()).thenReturn(SnapshotSourceType.EXTERNAL_ORDER);
        when(sourceSnapshot.sourceId()).thenReturn(1L);
        when(sourceSnapshot.calculationVersion()).thenReturn(SettlementCalculationVersion.PROFIT_V2);
        when(sourceSnapshot.channelFeeRate()).thenReturn(new BigDecimal("0.05"));
        when(sourceSnapshot.platformFeeRate()).thenReturn(new BigDecimal("0.03"));
        when(sourceSnapshot.storeOperationRate()).thenReturn(new BigDecimal("0.15"));
        when(sourceSnapshot.maintenanceFundRate()).thenReturn(new BigDecimal("0.10"));
        when(sourceSnapshot.channelReferralRate()).thenReturn(new BigDecimal("0.20"));
        when(sourceSnapshot.investorShareRate()).thenReturn(new BigDecimal("0.55"));
        when(settlementRepository.findSnapshot(10L)).thenReturn(Optional.of(sourceSnapshot));

        when(sku.batteryCostDailyAmount()).thenReturn(dailyBatteryCost);
        when(sku.batteryCostMonthlyAmount()).thenReturn(monthlyBatteryCost);
        when(productRepository.findSku(5L)).thenReturn(Optional.of(sku));
    }

    private void prepareCreatedEvent(LocalDateTime end, BigDecimal amount, BigDecimal batteryCost, String remark) {
        when(renewalRepository.nextPeriodNo(1L)).thenReturn(1);
        when(event.id()).thenReturn(42L);
        when(event.externalOrderId()).thenReturn(1L);
        when(event.eventNo()).thenReturn("ERN-contract");
        when(event.periodNo()).thenReturn(1);
        when(event.periodStartAt()).thenReturn(START);
        when(event.periodEndAt()).thenReturn(end);
        when(event.renewalAmount()).thenReturn(amount);
        when(event.batteryCostAmount()).thenReturn(batteryCost);
        when(event.eventStatus()).thenReturn("ACCRUED");
        when(event.renewalSource()).thenReturn(ExternalOrderRenewalSource.MANUAL);
        when(event.operatorAccountId()).thenReturn(7L);
        when(event.remark()).thenReturn(remark);
        when(renewalRepository.create(
            eq(1L),
            anyString(),
            eq(1),
            eq(START),
            eq(end),
            eq(amount),
            eq(new BigDecimal("129.00")),
            eq(batteryCost),
            eq(ExternalOrderRenewalSource.MANUAL),
            eq(7L),
            eq(remark)
        )).thenReturn(event);
        when(eventSnapshot.id()).thenReturn(99L);
        when(settlementService.createExternalRenewalSnapshot(42L, 10L, amount, batteryCost))
            .thenReturn(eventSnapshot);
        when(renewalRepository.attachSnapshot(42L, 99L)).thenReturn(event);
    }
}
