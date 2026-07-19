package com.xniu.rental.contract.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.contract.dto.ContractArchiveRequest;
import com.xniu.rental.contract.dto.ContractGenerateRequest;
import com.xniu.rental.contract.dto.ContractNotifyResponse;
import com.xniu.rental.contract.dto.ContractResponse;
import com.xniu.rental.contract.dto.ContractSignRequest;
import com.xniu.rental.contract.dto.ContractTemplateRequest;
import com.xniu.rental.contract.dto.ContractTemplateResponse;
import com.xniu.rental.contract.model.ContractNotify;
import com.xniu.rental.contract.model.ContractStatus;
import com.xniu.rental.contract.model.ContractTemplate;
import com.xniu.rental.contract.model.ContractTemplateStatus;
import com.xniu.rental.contract.model.ContractType;
import com.xniu.rental.contract.model.RentalContract;
import com.xniu.rental.contract.repository.ContractRepository;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.verify.model.RealNameStatus;
import com.xniu.rental.verify.repository.IdentityRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final OrderRepository orderRepository;
    private final IdentityRepository identityRepository;
    private final AuthorizationService authorizationService;

    public ContractService(ContractRepository contractRepository, OrderRepository orderRepository, IdentityRepository identityRepository, AuthorizationService authorizationService) {
        this.contractRepository = contractRepository;
        this.orderRepository = orderRepository;
        this.identityRepository = identityRepository;
        this.authorizationService = authorizationService;
    }

    public List<ContractTemplateResponse> listTemplates(String type, String status) {
        authorizationService.requirePermission("order.read");
        return contractRepository.listTemplates(parseTypeNullable(type), parseTemplateStatusNullable(status)).stream().map(this::toTemplateResponse).toList();
    }

    @Transactional
    public ContractTemplateResponse createTemplate(ContractTemplateRequest request) {
        authorizationService.requirePermission("order.operate");
        var template = contractRepository.createTemplate(new ContractRepository.TemplateCreateRow(
            request.templateCode(),
            request.templateName(),
            parseType(request.contractType()),
            request.versionNo(),
            request.providerTemplateId(),
            request.content(),
            request.remark()
        ));
        return toTemplateResponse(template);
    }

    @Transactional
    public ContractTemplateResponse updateTemplateStatus(Long id, ContractTemplateStatus status) {
        authorizationService.requirePermission("order.operate");
        return toTemplateResponse(contractRepository.updateTemplateStatus(id, status));
    }

    public List<ContractResponse> listAdminContracts(String status, Long orderId, Long userAccountId) {
        authorizationService.requirePermission("order.read");
        return contractRepository.listContracts(parseStatusNullable(status), orderId, userAccountId).stream().map(this::toResponse).toList();
    }

    public List<ContractNotifyResponse> listNotifies() {
        authorizationService.requirePermission("order.read");
        return contractRepository.listNotifies().stream().map(this::toNotifyResponse).toList();
    }

    public List<ContractResponse> listUserContracts(Long orderId) {
        var current = currentUserId();
        return contractRepository.listContracts(null, orderId, current).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ContractResponse generateContract(ContractGenerateRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(request.orderId());
        var template = request.templateId() == null
            ? contractRepository.findEnabledTemplate(ContractType.RENTAL).orElseThrow(() -> BusinessException.badRequest("请先启用租赁合同模板"))
            : contractRepository.findTemplate(request.templateId()).orElseThrow(() -> BusinessException.badRequest("合同模板不存在"));
        var identity = identityRepository.findLatestByUserAndOrder(order.userAccountId(), order.id())
            .orElseThrow(() -> BusinessException.badRequest("订单用户尚未实名"));
        if (identity.realNameStatus() != RealNameStatus.VERIFIED) {
            throw BusinessException.badRequest("订单用户实名未完成");
        }
        var existing = contractRepository.findLatestByOrder(order.id());
        if (existing.isPresent() && existing.get().contractStatus() != ContractStatus.CANCELLED) {
            return toResponse(existing.get());
        }
        var contract = contractRepository.createContract(new ContractRepository.ContractCreateRow(
            nextNo("CON"),
            order.id(),
            order.userAccountId(),
            order.merchantId(),
            order.storeId(),
            template.id(),
            template.contractType(),
            "ESIGN",
            render(template.content(), order, identity.realNameMasked(), identity.idNoMasked())
        ));
        return toResponse(contract);
    }

    @Transactional
    public ContractResponse startSign(Long id, ContractSignRequest request) {
        authorizationService.requirePermission("order.operate");
        var contract = ensureContract(id);
        authorizationService.requireStoreAccess(contract.merchantId(), contract.storeId());
        var externalFlowId = valueOr(request.externalFlowId(), "FLOW-" + contract.contractNo());
        var signUrl = valueOr(request.signUrl(), "/user/contract-sign/" + contract.id());
        return toResponse(contractRepository.markSigning(id, valueOr(request.provider(), "ESIGN"), externalFlowId, signUrl));
    }

    @Transactional
    public ContractResponse userConfirmSigned(Long id) {
        var current = currentUserId();
        var contract = ensureContract(id);
        if (!contract.userAccountId().equals(current)) {
            throw BusinessException.forbidden("不能签署其他用户合同");
        }
        var signed = contractRepository.markSigned(id);
        updateOrderAfterContract(signed.orderId(), current);
        return toResponse(signed);
    }

    @Transactional
    public ContractResponse archive(Long id, ContractArchiveRequest request) {
        authorizationService.requirePermission("order.operate");
        var contract = ensureContract(id);
        authorizationService.requireStoreAccess(contract.merchantId(), contract.storeId());
        return toResponse(contractRepository.archive(id, request.archivePdfUrl()));
    }

    @Transactional
    public boolean handleNotify(Map<String, String> params) {
        var flowId = params.get("external_flow_id");
        var contract = flowId == null ? null : contractRepository.findByExternalFlowId(flowId).orElse(null);
        if (contract == null) {
            contractRepository.createNotify(new ContractRepository.NotifyCreateRow(null, flowId, params.get("notify_id"), params.get("contract_status"), false, false, params.toString(), "合同不存在"));
            return false;
        }
        var status = params.get("contract_status");
        if ("SIGNED".equalsIgnoreCase(status) || "ARCHIVED".equalsIgnoreCase(status)) {
            var signed = contractRepository.markSigned(contract.id());
            updateOrderAfterContract(signed.orderId(), null);
        }
        contractRepository.createNotify(new ContractRepository.NotifyCreateRow(contract.id(), flowId, params.get("notify_id"), status, true, true, params.toString(), null));
        return true;
    }

    private void updateOrderAfterContract(Long orderId, Long operatorId) {
        var order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.orderStatus() != OrderStatus.PENDING_AGREEMENT) {
            return;
        }
        var updated = orderRepository.updateStatus(orderId, OrderStatus.PENDING_DEPOSIT_AUTH, null, null, null);
        orderRepository.addLog(orderId, order.orderStatus(), updated.orderStatus(), OrderOperationType.TRANSITION, operatorId, "合同签署完成，进入待免押/资金授权");
    }

    private String render(String content, RentalOrder order, String realNameMasked, String idNoMasked) {
        return content
            .replace("{{orderNo}}", order.orderNo())
            .replace("{{userName}}", valueOr(realNameMasked, "用户"))
            .replace("{{idNo}}", valueOr(idNoMasked, "已实名"))
            .replace("{{rentalAmount}}", money(order.rentalAmount()))
            .replace("{{signFeeAmount}}", money(order.signFeeAmount()))
            .replace("{{depositAmount}}", money(order.depositAmount()))
            .replace("{{payableAmount}}", money(order.payableAmount()))
            .replace("{{leaseText}}", order.leaseValue() + ("MONTH".equals(order.leaseUnit()) ? "个月" : "天"))
            .replace("{{totalPeriods}}", String.valueOf(order.totalPeriods()))
            .replace("{{signDate}}", LocalDate.now().toString());
    }

    private RentalOrder ensureOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
    }

    private RentalContract ensureContract(Long id) {
        return contractRepository.findContract(id).orElseThrow(() -> BusinessException.badRequest("合同不存在"));
    }

    private Long currentUserId() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current.account().id();
    }

    private String money(BigDecimal amount) {
        return "¥" + amount.toPlainString();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ContractType parseType(String value) {
        try {
            return ContractType.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的合同类型");
        }
    }

    private ContractType parseTypeNullable(String value) {
        return value == null || value.isBlank() ? null : parseType(value);
    }

    private ContractStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ContractStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的合同状态");
        }
    }

    private ContractTemplateStatus parseTemplateStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ContractTemplateStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的模板状态");
        }
    }

    private ContractTemplateResponse toTemplateResponse(ContractTemplate template) {
        return new ContractTemplateResponse(template.id(), template.templateCode(), template.templateName(), template.contractType().name(), template.versionNo(), template.providerTemplateId(), template.content(), template.status().name(), template.remark(), template.createdAt());
    }

    private ContractResponse toResponse(RentalContract contract) {
        return new ContractResponse(contract.id(), contract.contractNo(), contract.orderId(), contract.userAccountId(), contract.merchantId(), contract.storeId(), contract.templateId(), contract.contractType().name(), contract.contractStatus().name(), contract.provider(), contract.externalFlowId(), contract.signUrl(), contract.archivePdfUrl(), contract.renderedContent(), contract.failureReason(), contract.sentAt(), contract.signedAt(), contract.archivedAt(), contract.createdAt());
    }

    private ContractNotifyResponse toNotifyResponse(ContractNotify notify) {
        return new ContractNotifyResponse(notify.id(), notify.contractId(), notify.externalFlowId(), notify.notifyId(), notify.contractStatus(), notify.verified(), notify.processed(), notify.rawPayload(), notify.failureReason(), notify.receivedAt());
    }

    private String nextNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
