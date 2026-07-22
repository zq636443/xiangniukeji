package com.xniu.rental.order.service;

import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.dto.OrderBatchImportRequest;
import com.xniu.rental.order.dto.OrderBatchImportResponse;
import com.xniu.rental.order.dto.OrderBatchImportRowRequest;
import com.xniu.rental.order.dto.OrderBatchImportRowResultResponse;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderBatchImportService {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
        DateTimeFormatter.ofPattern("yyyy-M-d H:m")
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-M-d");

    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final StoreRepository storeRepository;
    private final OrderCreationService orderCreationService;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate transactionTemplate;

    public OrderBatchImportService(
        ProductRepository productRepository,
        AssetRepository assetRepository,
        StoreRepository storeRepository,
        OrderCreationService orderCreationService,
        AuthorizationService authorizationService,
        TransactionTemplate transactionTemplate
    ) {
        this.productRepository = productRepository;
        this.assetRepository = assetRepository;
        this.storeRepository = storeRepository;
        this.orderCreationService = orderCreationService;
        this.authorizationService = authorizationService;
        this.transactionTemplate = transactionTemplate;
    }

    public OrderBatchImportResponse batchImportAdmin(OrderBatchImportRequest request) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("order.operate");
        return importRows(request, null, false);
    }

    public OrderBatchImportResponse batchImportMerchant(Long storeId, OrderBatchImportRequest request) {
        authorizationService.requirePermission("order.create");
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return importRows(request, storeId, true);
    }

    private OrderBatchImportResponse importRows(OrderBatchImportRequest request, Long lockedStoreId, boolean merchantImport) {
        var results = new ArrayList<OrderBatchImportRowResultResponse>();
        int successCount = 0;
        for (int index = 0; index < request.rows().size(); index++) {
            var row = request.rows().get(index);
            var lineNo = row.lineNo() == null ? index + 2 : row.lineNo();
            try {
                var created = transactionTemplate.execute(status -> importRow(row, lockedStoreId, merchantImport));
                if (created == null) {
                    throw BusinessException.badRequest("导入失败");
                }
                results.add(new OrderBatchImportRowResultResponse(
                    lineNo,
                    true,
                    created.id(),
                    created.orderNo(),
                    created.customerName(),
                    created.customerPhone(),
                    "导入成功"
                ));
                successCount++;
            } catch (Exception exception) {
                results.add(new OrderBatchImportRowResultResponse(
                    lineNo,
                    false,
                    null,
                    null,
                    trimToNull(row.customerName()),
                    trimToNull(row.customerPhone()),
                    failureMessage(exception)
                ));
            }
        }
        return new OrderBatchImportResponse(
            request.rows().size(),
            successCount,
            request.rows().size() - successCount,
            results
        );
    }

    private OrderResponse importRow(OrderBatchImportRowRequest row, Long lockedStoreId, boolean merchantImport) {
        var storeSkuCode = requiredText(row.storeSkuCode(), "门店商品编码");
        var storeSku = productRepository.findStoreSkuByCode(storeSkuCode)
            .orElseThrow(() -> BusinessException.badRequest("门店商品编码不存在"));
        if (lockedStoreId != null && !lockedStoreId.equals(storeSku.storeId())) {
            throw BusinessException.badRequest("门店商品不属于当前门店");
        }
        var packageCode = requiredText(row.packageCode(), "SKU 编码");
        var packageTemplate = productRepository.findPackageByCode(packageCode)
            .orElseThrow(() -> BusinessException.badRequest("SKU 编码不存在"));
        var request = new OrderCreateRequest(
            optionalPositiveLong(row.userAccountId(), "用户账号ID"),
            requiredText(row.customerName(), "客户姓名"),
            requiredText(row.customerPhone(), "联系电话"),
            storeSku.id(),
            packageTemplate.id(),
            frameAssetId(row.frameSerialNo()),
            assetId(row.batterySerialNo(), AssetType.BATTERY),
            optionalDateTime(row.expectedPickupAt(), "预计取车时间"),
            optionalDateTime(row.orderedAt(), "下单时间"),
            requiredMoney(row.verificationAmount(), "实际核销金额")
        );
        return merchantImport
            ? orderCreationService.createMerchantOrder(request)
            : orderCreationService.createAdminOrder(request);
    }

    private Long assetId(String serialNo, AssetType assetType) {
        var normalized = trimToNull(serialNo);
        if (normalized == null) {
            return null;
        }
        return assetRepository.findBySerialNoAndType(normalized, assetType)
            .map(asset -> asset.id())
            .orElseThrow(() -> BusinessException.badRequest(assetType == AssetType.VEHICLE_FRAME ? "车架号不存在" : "电池号不存在"));
    }

    private Long frameAssetId(String serialNo) {
        var normalized = trimToNull(serialNo);
        if (normalized == null) {
            return null;
        }
        return assetRepository.findPrimaryOrderAssetBySerialNo(normalized)
            .map(asset -> asset.id())
            .orElseThrow(() -> BusinessException.badRequest("主资产编号不存在"));
    }

    private Long optionalPositiveLong(String value, String fieldName) {
        var normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            var parsed = new BigDecimal(normalized).longValueExact();
            if (parsed <= 0) {
                throw BusinessException.badRequest(fieldName + "必须大于0");
            }
            return parsed;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw BusinessException.badRequest(fieldName + "格式不正确");
        }
    }

    private BigDecimal requiredMoney(String value, String fieldName) {
        var normalized = requiredText(value, fieldName);
        try {
            var amount = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() < 0) {
                throw BusinessException.badRequest(fieldName + "不能小于0");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw BusinessException.badRequest(fieldName + "格式不正确");
        }
    }

    private LocalDateTime optionalDateTime(String value, String fieldName) {
        var normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace('/', '-').replace('T', ' ');
        for (var formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported template format.
            }
        }
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw BusinessException.badRequest(fieldName + "格式应为YYYY-MM-DD HH:mm");
        }
    }

    private String requiredText(String value, String fieldName) {
        var normalized = trimToNull(value);
        if (normalized == null) {
            throw BusinessException.badRequest("请填写" + fieldName);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String failureMessage(Exception exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank() ? "导入失败" : message;
    }
}
