package com.xniu.rental.externalorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalSource;
import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevision;
import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevisionType;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderVerificationRevisionRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.externalorder.service.ExternalOrderAutoRenewalService;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Immutability contracts for manual renewal facts and financially locked
 * system events. These tests deliberately exercise the reconciliation entry
 * point because it is the only background path allowed to replace an accrued
 * renewal snapshot and its income rows.
 */
@ExtendWith(MockitoExtension.class)
class ExternalOrderManualRenewalReconciliationContractTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private ExternalRentalOrderRepository orderRepository;

    @Mock
    private ExternalOrderRenewalRepository renewalRepository;

    @Mock
    private ExternalOrderVerificationRevisionRepository verificationRevisionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SettlementService settlementService;

    @Mock
    private SettlementIncomeService settlementIncomeService;

    @Mock
    private SettlementStatementService settlementStatementService;

    @Mock
    private SettlementIncomeRepository settlementIncomeRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementStatementRepository settlementStatementRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ExternalRentalOrder order;

    @InjectMocks
    private ExternalOrderAutoRenewalService service;

    @BeforeEach
    void prepareActiveOrder() {
        when(order.orderStatus()).thenReturn(ExternalRentalOrderStatus.ACTIVE);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
    }

    @Test
    void accruedManualEventIsNeverRepricedByVerificationTimelineReconciliation() {
        var manual = event(41L, ExternalOrderRenewalSource.MANUAL, "ACCRUED", "96.00");
        when(verificationRevisionRepository.listByOrder(1L)).thenReturn(List.of(revision("88.00")));
        when(renewalRepository.listByExternalOrder(1L)).thenReturn(List.of(manual));
        when(settlementStatementRepository.listDraftStatementMonthsBySource("EXTERNAL_RENEWAL", 41L))
            .thenReturn(List.of());
        when(settlementStatementRepository.hasDraftStatements("2026-08")).thenReturn(false);

        assertThat(service.reconcilePendingEvents(1L)).isZero();

        verifyNoReplacementWrites();
        verify(renewalRepository, never()).hasLockedStatementLinesByEventForUpdate(anyLong());
        verify(renewalRepository, never()).hasNonPendingIncomeByEventForUpdate(anyLong());
    }

    @Test
    void financiallySettledSystemEventKeepsItsFrozenAmountAndSnapshot() {
        var system = event(42L, ExternalOrderRenewalSource.SYSTEM, "ACCRUED", "129.00");
        when(verificationRevisionRepository.listByOrder(1L)).thenReturn(List.of(revision("96.00")));
        when(renewalRepository.listByExternalOrder(1L)).thenReturn(List.of(system));
        when(settlementStatementRepository.listDraftStatementMonthsBySource("EXTERNAL_RENEWAL", 42L))
            .thenReturn(List.of());
        when(settlementStatementRepository.hasDraftStatements("2026-08")).thenReturn(false);
        when(renewalRepository.hasLockedStatementLinesByEventForUpdate(42L)).thenReturn(false);
        when(renewalRepository.hasNonPendingIncomeByEventForUpdate(42L)).thenReturn(true);

        assertThat(service.reconcilePendingEvents(1L)).isZero();

        verify(renewalRepository).hasNonPendingIncomeByEventForUpdate(42L);
        verifyNoReplacementWrites();
    }

    private ExternalOrderRenewalEvent event(
        Long id,
        ExternalOrderRenewalSource source,
        String status,
        String amount
    ) {
        return new ExternalOrderRenewalEvent(
            id,
            1L,
            "ERN-contract-" + id,
            1,
            START,
            START.plusDays(30),
            new BigDecimal(amount),
            new BigDecimal("129.00"),
            BigDecimal.ZERO.setScale(2),
            99L,
            status,
            source,
            source == ExternalOrderRenewalSource.MANUAL ? 7L : null,
            source == ExternalOrderRenewalSource.MANUAL ? "人工核销" : null,
            START,
            START
        );
    }

    private ExternalOrderVerificationRevision revision(String amount) {
        return new ExternalOrderVerificationRevision(
            1L,
            1L,
            new BigDecimal(amount),
            START,
            ExternalOrderVerificationRevisionType.ORDER_EDIT,
            null,
            7L,
            START
        );
    }

    private void verifyNoReplacementWrites() {
        verify(settlementService, never()).rebuildExternalRenewalSnapshot(
            anyLong(), anyLong(), any(), any()
        );
        verify(settlementIncomeRepository, never()).deleteBySource(any(), anyLong());
        verify(renewalRepository, never()).updateAmount(anyLong(), any());
        verify(renewalRepository, never()).attachSnapshot(anyLong(), anyLong());
        verify(settlementIncomeService, never()).createExternalRenewalEntries(
            anyLong(), any(), anyLong(), any(), any()
        );
    }
}
