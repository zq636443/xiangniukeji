package com.xniu.rental.overdue.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.overdue.dto.OverdueCaseResponse;
import com.xniu.rental.overdue.dto.OverdueCollectionLogResponse;
import com.xniu.rental.overdue.model.CollectionStatus;
import com.xniu.rental.overdue.model.OverdueCase;
import com.xniu.rental.overdue.model.OverdueCollectionLog;
import com.xniu.rental.overdue.model.OverdueStatus;
import com.xniu.rental.overdue.repository.OverdueRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OverdueService {

    private final OverdueRepository overdueRepository;
    private final OrderRepository orderRepository;
    private final AuthorizationService authorizationService;

    public OverdueService(OverdueRepository overdueRepository, OrderRepository orderRepository, AuthorizationService authorizationService) {
        this.overdueRepository = overdueRepository;
        this.orderRepository = orderRepository;
        this.authorizationService = authorizationService;
    }

    public List<OverdueCaseResponse> listAdminCases(String statMonth, String overdueStatus, String collectionStatus, Long merchantId, Long storeId, Long userAccountId, Long storeSkuId) {
        authorizationService.requirePermission("order.read");
        return overdueRepository.listCases(statMonth, parseOverdueStatusNullable(overdueStatus), parseCollectionStatusNullable(collectionStatus), merchantId, storeId, userAccountId, storeSkuId).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<OverdueCaseResponse> listMerchantCases(String statMonth, String overdueStatus, String collectionStatus, Long storeId) {
        authorizationService.requirePermission("order.read");
        var current = AuthContext.get();
        if (current == null || current.account().merchantId() == null) {
            throw BusinessException.forbidden("当前账号不是商户账号");
        }
        var scopedStoreId = current.account().storeId() == null ? storeId : current.account().storeId();
        return overdueRepository.listCases(statMonth, parseOverdueStatusNullable(overdueStatus), parseCollectionStatusNullable(collectionStatus), current.account().merchantId(), scopedStoreId, null, null).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public OverdueCaseResponse updateCollection(Long id, String collectionStatus, String remark) {
        authorizationService.requirePermission("order.operate");
        var status = parseCollectionStatus(collectionStatus);
        var updated = overdueRepository.updateCollection(id, status, remark);
        overdueRepository.addCollectionLog(id, status, currentAccountId(), remark);
        return toResponse(updated);
    }

    @Transactional
    public void upsertFromDeductFailure(RentalBill bill, String failReason) {
        var order = orderRepository.findById(bill.orderId()).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        var unpaidAmount = bill.payableAmount().subtract(bill.paidAmount()).setScale(2, RoundingMode.HALF_UP);
        var now = LocalDateTime.now();
        var existing = overdueRepository.findByBillId(bill.id());
        if (existing.isPresent()) {
            overdueRepository.updateFailure(existing.get().id(), unpaidAmount, unpaidAmount, failReason, now);
        } else {
            overdueRepository.createCase(new OverdueRepository.CaseCreateRow(
                nextCaseNo(),
                YearMonth.from(now).toString(),
                order.id(),
                bill.id(),
                bill.userAccountId(),
                bill.merchantId(),
                bill.storeId(),
                order.storeSkuId(),
                order.skuId(),
                unpaidAmount,
                unpaidAmount,
                1,
                failReason,
                now
            ));
        }
        markOrderPendingSupplement(order, failReason);
    }

    @Transactional
    public void resolveByBillId(Long billId) {
        var existing = overdueRepository.findByBillId(billId);
        if (existing.isEmpty()) {
            return;
        }
        var updated = overdueRepository.resolveByBillId(billId);
        overdueRepository.addCollectionLog(updated.id(), CollectionStatus.RESOLVED, null, "账单已支付，逾期自动解决");
    }

    private void markOrderPendingSupplement(RentalOrder order, String reason) {
        if (order.orderStatus() == OrderStatus.COMPLETED || order.orderStatus() == OrderStatus.CANCELLED || order.orderStatus() == OrderStatus.EXCEPTION) {
            return;
        }
        if (order.orderStatus() == OrderStatus.PENDING_SUPPLEMENT) {
            return;
        }
        orderRepository.updateStatus(order.id(), OrderStatus.PENDING_SUPPLEMENT, null, null, null);
        orderRepository.addLog(order.id(), order.orderStatus(), OrderStatus.PENDING_SUPPLEMENT, OrderOperationType.TRANSITION, null, reason);
    }

    private OverdueStatus parseOverdueStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OverdueStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的逾期状态");
        }
    }

    private CollectionStatus parseCollectionStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseCollectionStatus(value);
    }

    private CollectionStatus parseCollectionStatus(String value) {
        try {
            return CollectionStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的催缴状态");
        }
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private OverdueCaseResponse toResponse(OverdueCase overdueCase) {
        return new OverdueCaseResponse(
            overdueCase.id(),
            overdueCase.caseNo(),
            overdueCase.statMonth(),
            overdueCase.orderId(),
            overdueCase.billId(),
            overdueCase.userAccountId(),
            overdueCase.merchantId(),
            overdueCase.storeId(),
            overdueCase.storeSkuId(),
            overdueCase.skuId(),
            overdueCase.overdueAmount(),
            overdueCase.unpaidAmount(),
            overdueCase.failCount(),
            overdueCase.lastFailReason(),
            overdueCase.lastDeductAt(),
            overdueCase.overdueStatus().name(),
            overdueCase.collectionStatus().name(),
            overdueCase.collectionRemark(),
            overdueCase.resolvedAt(),
            overdueCase.createdAt(),
            overdueRepository.listLogs(overdueCase.id()).stream().map(this::toLogResponse).toList()
        );
    }

    private OverdueCollectionLogResponse toLogResponse(OverdueCollectionLog log) {
        return new OverdueCollectionLogResponse(log.id(), log.overdueCaseId(), log.collectionStatus().name(), log.operatorAccountId(), log.remark(), log.createdAt());
    }

    private String nextCaseNo() {
        return "OD-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
