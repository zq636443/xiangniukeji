package com.xniu.rental.bill.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.dto.BillBatchResponse;
import com.xniu.rental.bill.dto.BillGenerateRequest;
import com.xniu.rental.bill.dto.BillGenerationResultResponse;
import com.xniu.rental.bill.dto.BillItemResponse;
import com.xniu.rental.bill.dto.BillLogResponse;
import com.xniu.rental.bill.dto.BillResponse;
import com.xniu.rental.bill.model.BillGenerationType;
import com.xniu.rental.bill.model.BillItemType;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.BillType;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.bill.model.RentalBillItem;
import com.xniu.rental.bill.model.RentalBillOperationLog;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillService {

    private final BillRepository billRepository;
    private final OrderRepository orderRepository;
    private final AuthorizationService authorizationService;

    public BillService(BillRepository billRepository, OrderRepository orderRepository, AuthorizationService authorizationService) {
        this.billRepository = billRepository;
        this.orderRepository = orderRepository;
        this.authorizationService = authorizationService;
    }

    public List<BillResponse> listBills(String status, Long orderId, Long storeId) {
        authorizationService.requirePermission("order.read");
        return billRepository.listBills(parseStatusNullable(status), orderId, storeId).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<BillResponse> listUserBills(String status, Long orderId) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (orderId != null) {
            var order = ensureOrder(orderId);
            if (order.userAccountId() == null || !order.userAccountId().equals(current.account().id())) {
                throw BusinessException.forbidden("不能查看其他用户账单");
            }
        }
        return billRepository.listBills(parseStatusNullable(status), orderId, null).stream()
            .filter(bill -> current.account().id().equals(bill.userAccountId()))
            .map(this::toResponse)
            .toList();
    }

    public List<BillResponse> listMerchantOrderBills(Long orderId) {
        authorizationService.requirePermission("order.read");
        var order = ensureOrder(orderId);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        return billRepository.listBills(null, orderId, order.storeId()).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<BillBatchResponse> listBatches() {
        authorizationService.requirePermission("order.read");
        return billRepository.listBatches().stream().map(this::toBatchResponse).toList();
    }

    @Transactional
    public BillGenerationResultResponse generate(BillGenerateRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(request.orderId());
        var billType = parseBillType(request.billType());
        return switch (billType) {
            case INITIAL -> generateInitial(order, request.dueAt(), request.remark());
            case PERIODIC -> generatePeriodic(order, request.periodNo(), request.dueAt(), request.remark());
            case RENEWAL -> generateRenewal(order, request.periodNo(), request.dueAt(), request.remark());
            case OVERDUE -> generateOverdue(order, request.periodNo(), request.overdueAmount(), request.dueAt(), request.remark());
        };
    }

    @Transactional
    public BillGenerationResultResponse generatePlan(Long orderId, String remark) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(orderId);
        return generatePlanInternal(order, remark);
    }

    @Transactional
    public BillGenerationResultResponse generatePlanForUser(Long orderId, String remark) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        var order = ensureOrder(orderId);
        if (order.userAccountId() == null || !order.userAccountId().equals(current.account().id())) {
            throw BusinessException.forbidden("不能生成其他用户订单账单");
        }
        return generatePlanInternal(order, remark);
    }

    @Transactional
    public BillGenerationResultResponse generatePlanForMerchant(Long orderId, String remark) {
        authorizationService.requirePermission("order.create");
        var order = ensureOrder(orderId);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        return generatePlanInternal(order, remark);
    }

    private BillGenerationResultResponse generatePlanInternal(RentalOrder order, String remark) {
        var batchNo = nextBatchNo();
        var bills = new ArrayList<RentalBill>();
        createInitialIfAbsent(order, null, remark, batchNo).ifCreated(bills::add);
        for (var periodNo = 2; periodNo <= safeTotalPeriods(order); periodNo++) {
            createPeriodicIfAbsent(order, periodNo, null, remark, batchNo).ifCreated(bills::add);
        }
        var batch = billRepository.createBatch(batchNo, BillGenerationType.PLAN, order.id(), bills.size(), defaultRemark(remark, "生成整单账单计划"));
        return new BillGenerationResultResponse(toBatchResponse(batch), bills.stream().map(this::toResponse).toList());
    }

    @Transactional
    public BillResponse cancelBill(Long id, String remark) {
        authorizationService.requirePermission("order.operate");
        var bill = ensureBill(id);
        if (bill.billStatus() == BillStatus.PAID) {
            throw BusinessException.badRequest("已支付账单不能关闭");
        }
        if (bill.billStatus() == BillStatus.CANCELLED) {
            return toResponse(bill);
        }
        var updated = billRepository.updateStatus(id, BillStatus.CANCELLED);
        billRepository.addLog(id, bill.billStatus(), BillStatus.CANCELLED, BillOperationType.CANCEL, currentAccountId(), defaultRemark(remark, "手动关闭账单"));
        return toResponse(updated);
    }

    private BillGenerationResultResponse generateInitial(RentalOrder order, LocalDateTime dueAt, String remark) {
        var batchNo = nextBatchNo();
        var created = new ArrayList<RentalBill>();
        createInitialIfAbsent(order, dueAt, remark, batchNo).ifCreated(created::add);
        var batch = billRepository.createBatch(batchNo, BillGenerationType.INITIAL, order.id(), created.size(), defaultRemark(remark, "生成首期账单"));
        return new BillGenerationResultResponse(toBatchResponse(batch), created.stream().map(this::toResponse).toList());
    }

    private BillGenerationResultResponse generatePeriodic(RentalOrder order, Integer requestedPeriodNo, LocalDateTime dueAt, String remark) {
        var periodNo = requestedPeriodNo == null ? nextPeriodicPeriodNo(order) : requestedPeriodNo;
        if (periodNo < 2 || periodNo > safeTotalPeriods(order)) {
            throw BusinessException.badRequest("周期账单期数必须在 2 到总期数之间");
        }
        var batchNo = nextBatchNo();
        var created = new ArrayList<RentalBill>();
        createPeriodicIfAbsent(order, periodNo, dueAt, remark, batchNo).ifCreated(created::add);
        var batch = billRepository.createBatch(batchNo, BillGenerationType.PERIODIC, order.id(), created.size(), defaultRemark(remark, "生成周期账单"));
        return new BillGenerationResultResponse(toBatchResponse(batch), created.stream().map(this::toResponse).toList());
    }

    private BillGenerationResultResponse generateOverdue(RentalOrder order, Integer requestedPeriodNo, BigDecimal overdueAmount, LocalDateTime dueAt, String remark) {
        if (overdueAmount == null || overdueAmount.signum() <= 0) {
            throw BusinessException.badRequest("逾期账单金额必须大于 0");
        }
        var periodNo = requestedPeriodNo == null ? nextOverduePeriodNo(order) : requestedPeriodNo;
        var batchNo = nextBatchNo();
        var existing = billRepository.findExisting(order.id(), BillType.OVERDUE, periodNo);
        var created = new ArrayList<RentalBill>();
        if (existing.isEmpty()) {
            var bill = createBill(order, BillType.OVERDUE, periodNo, dueAt == null ? LocalDateTime.now() : dueAt, overdueAmount.setScale(2, RoundingMode.HALF_UP), overdueAmount.setScale(2, RoundingMode.HALF_UP), defaultRemark(remark, "生成逾期账单"), batchNo);
            billRepository.addItem(bill.id(), BillItemType.OVERDUE_FEE, "逾期费用", overdueAmount.setScale(2, RoundingMode.HALF_UP));
            billRepository.addLog(bill.id(), null, BillStatus.PENDING_PAYMENT, BillOperationType.GENERATE, currentAccountId(), defaultRemark(remark, "生成逾期账单"));
            created.add(bill);
        }
        var batch = billRepository.createBatch(batchNo, BillGenerationType.OVERDUE, order.id(), created.size(), defaultRemark(remark, "生成逾期账单"));
        return new BillGenerationResultResponse(toBatchResponse(batch), created.stream().map(this::toResponse).toList());
    }

    private BillGenerationResultResponse generateRenewal(RentalOrder order, Integer requestedPeriodNo, LocalDateTime dueAt, String remark) {
        if (!Boolean.TRUE.equals(order.autoRenewEnabled())) {
            throw BusinessException.badRequest("当前订单未开启自动续租");
        }
        if (order.renewalAmount() == null || order.renewalAmount().signum() <= 0 || order.renewalValue() == null || order.renewalValue() <= 0) {
            throw BusinessException.badRequest("当前订单续租规则不完整");
        }
        var periodNo = requestedPeriodNo == null ? nextRenewalPeriodNo(order) : requestedPeriodNo;
        var batchNo = nextBatchNo();
        var existing = billRepository.findExisting(order.id(), BillType.RENEWAL, periodNo);
        var created = new ArrayList<RentalBill>();
        if (existing.isEmpty()) {
            var payableAmount = order.renewalAmount().setScale(2, RoundingMode.HALF_UP);
            var bill = createBill(order, BillType.RENEWAL, periodNo, dueAt == null ? LocalDateTime.now() : dueAt, payableAmount, BigDecimal.ZERO, defaultRemark(remark, "生成续租账单"), batchNo);
            billRepository.addItem(bill.id(), BillItemType.RENEWAL_RENT, "第 " + periodNo + " 期续租租金", payableAmount);
            billRepository.addLog(bill.id(), null, BillStatus.PENDING_PAYMENT, BillOperationType.GENERATE, currentAccountId(), defaultRemark(remark, "生成续租账单"));
            created.add(bill);
        }
        var batch = billRepository.createBatch(batchNo, BillGenerationType.RENEWAL, order.id(), created.size(), defaultRemark(remark, "生成续租账单"));
        return new BillGenerationResultResponse(toBatchResponse(batch), created.stream().map(this::toResponse).toList());
    }

    private CreatedBill createInitialIfAbsent(RentalOrder order, LocalDateTime dueAt, String remark, String batchNo) {
        var existing = billRepository.findExisting(order.id(), BillType.INITIAL, 1);
        if (existing.isPresent()) {
            return CreatedBill.existing();
        }
        var rentAmount = periodRentAmount(order, 1);
        var total = rentAmount.add(order.signFeeAmount()).add(order.depositAmount()).setScale(2, RoundingMode.HALF_UP);
        var bill = createBill(order, BillType.INITIAL, 1, dueAt == null ? initialDueAt(order) : dueAt, total, BigDecimal.ZERO, defaultRemark(remark, "首期账单"), batchNo);
        billRepository.addItem(bill.id(), BillItemType.RENT, "首期租金", rentAmount);
        if (order.signFeeAmount().signum() > 0) {
            billRepository.addItem(bill.id(), BillItemType.SIGN_FEE, "签单费", order.signFeeAmount());
        }
        if (order.depositAmount().signum() > 0) {
            billRepository.addItem(bill.id(), BillItemType.DEPOSIT, "押金", order.depositAmount());
        }
        billRepository.addLog(bill.id(), null, BillStatus.PENDING_PAYMENT, BillOperationType.GENERATE, currentAccountId(), defaultRemark(remark, "生成首期账单"));
        return CreatedBill.created(bill);
    }

    private CreatedBill createPeriodicIfAbsent(RentalOrder order, Integer periodNo, LocalDateTime dueAt, String remark, String batchNo) {
        var existing = billRepository.findExisting(order.id(), BillType.PERIODIC, periodNo);
        if (existing.isPresent()) {
            return CreatedBill.existing();
        }
        var rentAmount = periodRentAmount(order, periodNo);
        var bill = createBill(order, BillType.PERIODIC, periodNo, dueAt == null ? periodDueAt(order, periodNo) : dueAt, rentAmount, BigDecimal.ZERO, defaultRemark(remark, "周期账单"), batchNo);
        billRepository.addItem(bill.id(), BillItemType.RENT, "第 " + periodNo + " 期租金", rentAmount);
        billRepository.addLog(bill.id(), null, BillStatus.PENDING_PAYMENT, BillOperationType.GENERATE, currentAccountId(), defaultRemark(remark, "生成周期账单"));
        return CreatedBill.created(bill);
    }

    private RentalBill createBill(RentalOrder order, BillType billType, Integer periodNo, LocalDateTime dueAt, BigDecimal payableAmount, BigDecimal overdueAmount, String remark, String batchNo) {
        return billRepository.createBill(new BillRepository.BillCreateRow(
            nextBillNo(),
            order.id(),
            order.userAccountId(),
            order.merchantId(),
            order.storeId(),
            billType,
            periodNo,
            BillStatus.PENDING_PAYMENT,
            dueAt,
            payableAmount,
            BigDecimal.ZERO,
            overdueAmount,
            remark,
            batchNo
        ));
    }

    private BigDecimal periodRentAmount(RentalOrder order, int periodNo) {
        var totalPeriods = safeTotalPeriods(order);
        var base = order.rentalAmount().divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.DOWN);
        if (periodNo == totalPeriods) {
            return order.rentalAmount().subtract(base.multiply(BigDecimal.valueOf(totalPeriods - 1))).setScale(2, RoundingMode.HALF_UP);
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime periodDueAt(RentalOrder order, int periodNo) {
        var base = order.leaseStartedAt() != null ? order.leaseStartedAt() : order.expectedPickupAt();
        if (base == null) {
            base = order.orderedAt() != null ? order.orderedAt() : order.createdAt();
        }
        if ("MONTH".equals(order.leaseUnit())) {
            return base.plusDays(30L * (periodNo - 1L));
        }
        var stepDays = Math.max(1, order.leaseValue() / safeTotalPeriods(order));
        return base.plusDays((long) stepDays * (periodNo - 1L));
    }

    private LocalDateTime initialDueAt(RentalOrder order) {
        if (order.orderedAt() != null) {
            return order.orderedAt();
        }
        return order.createdAt() == null ? LocalDateTime.now() : order.createdAt();
    }

    private int safeTotalPeriods(RentalOrder order) {
        return Math.max(order.totalPeriods() == null ? 1 : order.totalPeriods(), 1);
    }

    private int nextPeriodicPeriodNo(RentalOrder order) {
        return billRepository.findMaxPeriodNo(order.id(), BillType.PERIODIC).orElse(1) + 1;
    }

    private int nextOverduePeriodNo(RentalOrder order) {
        return billRepository.findMaxPeriodNo(order.id(), BillType.OVERDUE).orElse(0) + 1;
    }

    private int nextRenewalPeriodNo(RentalOrder order) {
        return billRepository.findMaxPeriodNo(order.id(), BillType.RENEWAL).orElse(safeTotalPeriods(order)) + 1;
    }

    private RentalOrder ensureOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
    }

    private RentalBill ensureBill(Long id) {
        return billRepository.findBill(id).orElseThrow(() -> BusinessException.badRequest("账单不存在"));
    }

    private BillResponse toResponse(RentalBill bill) {
        return new BillResponse(
            bill.id(),
            bill.billNo(),
            bill.orderId(),
            bill.userAccountId(),
            bill.merchantId(),
            bill.storeId(),
            bill.billType().name(),
            bill.periodNo(),
            bill.billStatus().name(),
            bill.dueAt(),
            bill.payableAmount(),
            bill.paidAmount(),
            bill.overdueAmount(),
            bill.paidAt(),
            bill.cancelledAt(),
            bill.remark(),
            bill.generatedBatchNo(),
            bill.createdAt(),
            billRepository.listItems(bill.id()).stream().map(this::toItemResponse).toList(),
            billRepository.listLogs(bill.id()).stream().map(this::toLogResponse).toList()
        );
    }

    private BillItemResponse toItemResponse(RentalBillItem item) {
        return new BillItemResponse(item.id(), item.itemType().name(), item.itemName(), item.amount());
    }

    private BillLogResponse toLogResponse(RentalBillOperationLog log) {
        return new BillLogResponse(
            log.id(),
            log.billId(),
            log.fromStatus() == null ? null : log.fromStatus().name(),
            log.toStatus().name(),
            log.operationType().name(),
            log.operatorAccountId(),
            log.remark(),
            log.createdAt()
        );
    }

    private BillBatchResponse toBatchResponse(com.xniu.rental.bill.model.BillGenerationBatch batch) {
        return new BillBatchResponse(batch.id(), batch.batchNo(), batch.generationType().name(), batch.orderId(), batch.generatedCount(), batch.remark(), batch.createdAt());
    }

    private BillType parseBillType(String value) {
        try {
            return BillType.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的账单类型");
        }
    }

    private BillStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BillStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的账单状态");
        }
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private String defaultRemark(String remark, String fallback) {
        return remark == null || remark.isBlank() ? fallback : remark;
    }

    private String nextBillNo() {
        return "BIL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nextBatchNo() {
        return "BGB-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record CreatedBill(boolean created, RentalBill bill) {
        static CreatedBill created(RentalBill bill) {
            return new CreatedBill(true, bill);
        }

        static CreatedBill existing() {
            return new CreatedBill(false, null);
        }

        void ifCreated(java.util.function.Consumer<RentalBill> consumer) {
            if (created) {
                consumer.accept(bill);
            }
        }
    }
}
