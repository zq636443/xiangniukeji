package com.xniu.rental.verify.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.verify.dto.IdentityImageRequest;
import com.xniu.rental.verify.dto.IdentityVerificationResponse;
import com.xniu.rental.verify.dto.RealNameConfirmRequest;
import com.xniu.rental.verify.model.IdentityVerification;
import com.xniu.rental.verify.model.RealNameStatus;
import com.xniu.rental.verify.repository.IdentityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private final IdentityRepository identityRepository;
    private final OrderRepository orderRepository;
    private final AuthorizationService authorizationService;

    public IdentityService(IdentityRepository identityRepository, OrderRepository orderRepository, AuthorizationService authorizationService) {
        this.identityRepository = identityRepository;
        this.orderRepository = orderRepository;
        this.authorizationService = authorizationService;
    }

    public List<IdentityVerificationResponse> listAdmin(Long userAccountId, Long orderId, String status) {
        authorizationService.requirePermission("order.read");
        return identityRepository.list(userAccountId, orderId, parseStatusNullable(status)).stream().map(this::toResponse).toList();
    }

    public List<IdentityVerificationResponse> listMine(Long orderId) {
        var current = currentUserId();
        return identityRepository.list(current, orderId, null).stream().map(this::toResponse).toList();
    }

    @Transactional
    public IdentityVerificationResponse uploadImages(IdentityImageRequest request) {
        var current = currentUserId();
        var order = orderRepository.findById(request.orderId()).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        if (order.userAccountId() == null || !order.userAccountId().equals(current)) {
            throw BusinessException.forbidden("不能为其他用户订单实名");
        }
        var existing = identityRepository.findLatestByUserAndOrder(current, request.orderId());
        var record = existing
            .map(item -> identityRepository.updateImages(item.id(), request.frontImageUrl(), request.backImageUrl()))
            .orElseGet(() -> identityRepository.createImages(current, request.orderId(), request.frontImageUrl(), request.backImageUrl()));
        record = identityRepository.markOcrSuccess(record.id(), "MANUAL_CONFIRM");
        return toResponse(record);
    }

    @Transactional
    public IdentityVerificationResponse confirmRealName(Long id, RealNameConfirmRequest request) {
        var current = currentUserId();
        var record = identityRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("实名记录不存在"));
        if (!record.userAccountId().equals(current)) {
            throw BusinessException.forbidden("不能确认其他用户实名");
        }
        var updated = identityRepository.markVerified(id, new IdentityRepository.VerifiedRow(
            maskName(request.realName()),
            maskIdNo(request.idNo()),
            hashIdNo(request.idNo()),
            request.gender(),
            request.birthDate(),
            maskAddress(request.address()),
            "MANUAL_CONFIRM",
            "CERT-" + id
        ));
        updateOrderAfterRealName(updated.orderId(), current);
        return toResponse(updated);
    }

    private void updateOrderAfterRealName(Long orderId, Long operatorId) {
        if (orderId == null) {
            return;
        }
        var order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.orderStatus() != OrderStatus.PENDING_REAL_NAME) {
            return;
        }
        var updated = orderRepository.updateStatus(orderId, OrderStatus.PENDING_AGREEMENT, null, null, null);
        orderRepository.addLog(orderId, order.orderStatus(), updated.orderStatus(), OrderOperationType.TRANSITION, operatorId, "实名完成，进入待签约");
    }

    private Long currentUserId() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current.account().id();
    }

    private RealNameStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RealNameStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的实名状态");
        }
    }

    private String maskName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1) + "*".repeat(Math.max(value.length() - 1, 1));
    }

    private String maskIdNo(String value) {
        var clean = value == null ? "" : value.trim();
        if (clean.length() <= 8) {
            return "****";
        }
        return clean.substring(0, 4) + "**********" + clean.substring(clean.length() - 4);
    }

    private String maskAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 6 ? value.substring(0, 1) + "***" : value.substring(0, 6) + "***";
    }

    private String hashIdNo(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw BusinessException.badRequest("身份证号哈希失败");
        }
    }

    private IdentityVerificationResponse toResponse(IdentityVerification record) {
        return new IdentityVerificationResponse(
            record.id(),
            record.userAccountId(),
            record.orderId(),
            record.frontImageUrl(),
            record.backImageUrl(),
            record.ocrStatus().name(),
            record.realNameStatus().name(),
            record.realNameMasked(),
            record.idNoMasked(),
            record.gender(),
            record.birthDate(),
            record.addressMasked(),
            record.ocrProvider(),
            record.certifyProvider(),
            record.externalCertifyId(),
            record.failureReason(),
            record.verifiedAt(),
            record.createdAt()
        );
    }
}
