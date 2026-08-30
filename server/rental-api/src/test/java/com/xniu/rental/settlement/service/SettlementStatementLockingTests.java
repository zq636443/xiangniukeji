package com.xniu.rental.settlement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xniu.rental.asset.repository.AssetFulfillmentRepository;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Guards the two month-rebuild entry points' lock contracts.  The
 * already-locked path is used while an order mutation owns the month rows;
 * taking the month-wide order scan there would reintroduce an order/month
 * lock inversion.
 */
class SettlementStatementLockingTests {

    private SettlementStatementRepository statementRepository;
    private SettlementStatementService service;

    @BeforeEach
    void setUp() {
        statementRepository = mock(SettlementStatementRepository.class);
        var incomeRepository = mock(SettlementIncomeRepository.class);
        var settlementRepository = mock(SettlementRepository.class);
        var incomeService = mock(SettlementIncomeService.class);
        var fulfillmentRepository = mock(AssetFulfillmentRepository.class);
        var assetRepository = mock(AssetRepository.class);
        var authorizationService = mock(AuthorizationService.class);

        when(statementRepository.hasLockedStatements(anyString())).thenReturn(false);
        when(statementRepository.listPaidBillItems(any(), any())).thenReturn(List.of());
        when(statementRepository.listExternalOrderItems(any(), any())).thenReturn(List.of());
        when(statementRepository.listExternalRenewalItems(any(), any())).thenReturn(List.of());
        when(statementRepository.listMaintenanceCosts(any(), any())).thenReturn(List.of());
        when(settlementRepository.findSnapshotsByIds(anyList())).thenReturn(List.of());
        when(fulfillmentRepository.listUsageByOrders(anyList())).thenReturn(List.of());

        service = new SettlementStatementService(
            statementRepository,
            incomeRepository,
            settlementRepository,
            incomeService,
            fulfillmentRepository,
            assetRepository,
            authorizationService
        );
    }

    @Test
    void alreadyLockedRebuildDoesNotScanOtherExternalOrders() {
        service.regenerateUnlockedMonthAlreadyLocked("2026-08");

        verify(statementRepository).lockStatementsByMonthForUpdate("2026-08");
        verify(statementRepository, never()).lockExternalOrdersForMonthForUpdate(any(), any());
    }

    @Test
    void publicRegenerationKeepsOrderBeforeMonthLockContract() {
        service.regenerateUnlockedMonth("2026-08");

        var order = inOrder(statementRepository);
        order.verify(statementRepository).lockExternalOrdersForMonthForUpdate(any(), any());
        order.verify(statementRepository).lockStatementsByMonthForUpdate("2026-08");
    }
}
