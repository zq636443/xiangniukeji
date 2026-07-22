package com.xniu.rental.merchant.service;

import com.xniu.rental.auth.model.Account;
import com.xniu.rental.auth.model.AccountStatus;
import com.xniu.rental.auth.model.AccountType;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.repository.AuthQueryRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.auth.service.PasswordHasher;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.dto.EmployeeRequest;
import com.xniu.rental.merchant.dto.EmployeeResponse;
import com.xniu.rental.merchant.dto.MerchantRequest;
import com.xniu.rental.merchant.dto.MerchantResponse;
import com.xniu.rental.merchant.dto.StoreRequest;
import com.xniu.rental.merchant.dto.StoreResponse;
import com.xniu.rental.merchant.model.Merchant;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.EmployeeRepository;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.pay.config.AlipayProperties;
import com.xniu.rental.pay.service.AlipayGatewayClient;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.service.SettlementService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final AuthQueryRepository authQueryRepository;
    private final AuthorizationService authorizationService;
    private final PasswordHasher passwordHasher;
    private final AlipayGatewayClient alipayGatewayClient;
    private final AlipayProperties alipayProperties;
    private final SettlementService settlementService;
    private final ProductRepository productRepository;

    public MerchantService(
        MerchantRepository merchantRepository,
        StoreRepository storeRepository,
        EmployeeRepository employeeRepository,
        AccountRepository accountRepository,
        AuthQueryRepository authQueryRepository,
        AuthorizationService authorizationService,
        PasswordHasher passwordHasher,
        AlipayGatewayClient alipayGatewayClient,
        AlipayProperties alipayProperties,
        SettlementService settlementService,
        ProductRepository productRepository
    ) {
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.authQueryRepository = authQueryRepository;
        this.authorizationService = authorizationService;
        this.passwordHasher = passwordHasher;
        this.alipayGatewayClient = alipayGatewayClient;
        this.alipayProperties = alipayProperties;
        this.settlementService = settlementService;
        this.productRepository = productRepository;
    }

    public List<MerchantResponse> listMerchants(String keyword) {
        authorizationService.requirePermission("merchant.read");
        return merchantRepository.list(keyword).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MerchantResponse createMerchant(MerchantRequest request) {
        authorizationService.requirePermission("merchant.write");
        var merchant = merchantRepository.create(
            nextCode("M"),
            request.merchantName(),
            request.contactName(),
            request.contactPhone(),
            request.businessLicenseNo()
        );
        if (Boolean.TRUE.equals(request.createOwnerAccount())) {
            validateOwnerAccountRequest(request);
            createMerchantOwnerAccount(merchant.id(), request.ownerUsername(), request.ownerDisplayName(), request.ownerPhone(), request.ownerPassword());
        }
        return toResponse(merchant);
    }

    @Transactional
    public MerchantResponse updateMerchant(Long id, MerchantRequest request) {
        authorizationService.requirePermission("merchant.write");
        ensureMerchantExists(id);
        return toResponse(merchantRepository.update(
            id,
            request.merchantName(),
            request.contactName(),
            request.contactPhone(),
            request.businessLicenseNo()
        ));
    }

    @Transactional
    public MerchantResponse updateMerchantStatus(Long id, MerchantStatus status) {
        authorizationService.requirePermission("merchant.write");
        ensureMerchantExists(id);
        if (status == MerchantStatus.DISABLED) {
            productRepository.offShelfStoreSkusByMerchant(id);
        }
        return toResponse(merchantRepository.updateStatus(id, status));
    }

    public List<StoreResponse> listStores(Long merchantId, String keyword) {
        authorizationService.requirePermission("store.read");
        return storeRepository.list(merchantId, keyword).stream().map(this::toResponse).toList();
    }

    @Transactional
    public StoreResponse createStore(StoreRequest request) {
        authorizationService.requirePermission("store.write");
        ensureEnabledMerchant(request.merchantId());
        var storeCode = nextCode("S");
        var qrContent = createStoreQrContent(storeCode, request.storeName());
        var store = storeRepository.create(
            request.merchantId(),
            storeCode,
            request.storeName(),
            request.address(),
            request.businessHours(),
            request.longitude(),
            request.latitude(),
            qrContent
        );
        settlementService.initializeStoreProfitRule(store.id());
        return toResponse(store);
    }

    @Transactional
    public StoreResponse regenerateStoreQrcode(Long id) {
        authorizationService.requirePermission("store.write");
        var store = ensureStoreExists(id);
        var qrContent = createOfficialStoreQrContent(store.storeCode(), store.storeName());
        return toResponse(storeRepository.updateQrContent(id, qrContent));
    }

    @Transactional
    public StoreResponse updateStore(Long id, StoreRequest request) {
        authorizationService.requirePermission("store.write");
        var store = ensureStoreExists(id);
        if (!store.merchantId().equals(request.merchantId())) {
            throw BusinessException.badRequest("门店所属商户不可在编辑时变更");
        }
        return toResponse(storeRepository.update(
            id,
            request.storeName(),
            request.address(),
            request.businessHours(),
            request.longitude(),
            request.latitude()
        ));
    }

    @Transactional
    public void deleteStore(Long id) {
        authorizationService.requirePermission("store.write");
        ensureStoreExists(id);
        var blockers = new ArrayList<String>();
        addBlocker(blockers, "员工账号", storeRepository.countBoundAccounts(id));
        addBlocker(blockers, "账号门店授权", storeRepository.countStoreScopes(id));
        addBlocker(blockers, "门店商品", storeRepository.countStoreSkus(id));
        addBlocker(blockers, "在库资产", storeRepository.countCurrentAssets(id));
        addBlocker(blockers, "租赁订单", storeRepository.countOrders(id));
        addBlocker(blockers, "补录订单", storeRepository.countExternalOrders(id));
        addBlocker(blockers, "核销记录", storeRepository.countVouchers(id));
        addBlocker(blockers, "维修记录", storeRepository.countMaintenanceRecords(id));
        addBlocker(blockers, "门店配件库存", storeRepository.countStorePartStocks(id));
        addBlocker(blockers, "门店配件流水", storeRepository.countStorePartStockLogs(id));
        if (!blockers.isEmpty()) {
            throw BusinessException.badRequest("门店仍存在关联数据，暂不可删除：" + String.join("、", blockers));
        }
        settlementService.deleteStoreProfitRules(id);
        storeRepository.deleteById(id);
    }

    @Transactional
    public StoreResponse updateStoreStatus(Long id, StoreStatus status) {
        authorizationService.requirePermission("store.write");
        ensureStoreExists(id);
        if (status == StoreStatus.DISABLED) {
            productRepository.offShelfStoreSkusByStore(id);
        }
        return toResponse(storeRepository.updateStatus(id, status));
    }

    public List<EmployeeResponse> listEmployees(Long merchantId) {
        authorizationService.requirePermission("merchant.read");
        ensureMerchantExists(merchantId);
        return employeeRepository.listByMerchant(merchantId).stream().map(this::toEmployeeResponse).toList();
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        authorizationService.requirePermission("merchant.write");
        ensureEnabledMerchant(request.merchantId());
        var accountType = resolveEmployeeAccountType(request.roleCode());
        var storeIds = normalizeStoreIds(request);
        var account = createMerchantScopedAccount(
            request.merchantId(),
            accountType,
            request.username(),
            request.displayName(),
            request.phone(),
            request.password(),
            storeIds
        );
        return toEmployeeResponse(account);
    }

    public Account createMerchantOwnerAccount(Long merchantId, String username, String displayName, String phone, String password) {
        return createMerchantScopedAccount(
            merchantId,
            AccountType.MERCHANT_OWNER,
            username,
            displayName,
            phone,
            password,
            List.of()
        );
    }

    @Transactional
    public EmployeeResponse updateEmployeeStatus(Long accountId, AccountStatus status) {
        authorizationService.requirePermission("merchant.write");
        return toEmployeeResponse(employeeRepository.updateStatus(accountId, status));
    }

    public List<StoreResponse> listMyStores() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        var scopes = current.account().storeScopes();
        if (scopes.isEmpty()) {
            return List.of();
        }
        var stores = scopes.stream()
            .flatMap(scope -> {
                if ("ALL_MERCHANT_STORES".equals(scope.scopeType())) {
                    return storeRepository.findByMerchantId(scope.merchantId()).stream();
                }
                return storeRepository.findByIds(List.of(scope.storeId())).stream();
            })
            .filter(store -> store.status() == StoreStatus.ENABLED)
            .distinct()
            .toList();
        return stores.stream().map(this::toResponse).toList();
    }

    public StoreResponse getMyStore(Long storeId) {
        var store = ensureStoreExists(storeId);
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return toResponse(store);
    }

    private List<Long> normalizeStoreIds(EmployeeRequest request) {
        var role = resolveEmployeeAccountType(request.roleCode());
        if (role == AccountType.MERCHANT_OWNER) {
            return List.of();
        }
        var storeIds = request.storeIds() == null || request.storeIds().isEmpty()
            ? (request.storeId() == null ? List.<Long>of() : List.of(request.storeId()))
            : request.storeIds();
        if (storeIds.isEmpty()) {
            throw BusinessException.badRequest("店长、店员或维修人员必须至少授权一个门店");
        }
        var stores = storeRepository.findByIds(storeIds);
        if (stores.size() != storeIds.size() || stores.stream().anyMatch(store -> !store.merchantId().equals(request.merchantId()))) {
            throw BusinessException.badRequest("门店不属于所选商户");
        }
        if (stores.stream().anyMatch(store -> store.status() != StoreStatus.ENABLED)) {
            throw BusinessException.badRequest("停用门店不能授权给账号");
        }
        return storeIds;
    }

    private AccountType resolveEmployeeAccountType(String roleCode) {
        return switch (roleCode) {
            case "MERCHANT_OWNER" -> AccountType.MERCHANT_OWNER;
            case "STORE_MANAGER" -> AccountType.STORE_MANAGER;
            case "STORE_OPERATOR" -> AccountType.STORE_OPERATOR;
            case "STORE_STAFF" -> AccountType.STORE_STAFF;
            case "MAINTENANCE_STAFF" -> AccountType.MAINTENANCE_STAFF;
            case "WAREHOUSE_STAFF" -> AccountType.WAREHOUSE_STAFF;
            default -> throw BusinessException.badRequest("不支持的商户角色");
        };
    }

    private Account createMerchantScopedAccount(
        Long merchantId,
        AccountType accountType,
        String username,
        String displayName,
        String phone,
        String password,
        List<Long> storeIds
    ) {
        if (employeeRepository.findByUsername(username).isPresent()) {
            throw BusinessException.badRequest("账号已存在");
        }
        var account = accountRepository.createManual(
            accountType,
            username,
            phone,
            displayName,
            passwordHasher.encode(password),
            merchantId,
            storeIds.isEmpty() ? null : storeIds.getFirst(),
            null
        );
        accountRepository.bindRole(account.id(), accountType.name());
        employeeRepository.replaceStoreScopes(account.id(), merchantId, storeIds);
        return account;
    }

    private void validateOwnerAccountRequest(MerchantRequest request) {
        if (request.ownerUsername() == null || request.ownerUsername().isBlank()) {
            throw BusinessException.badRequest("请输入商户主账号登录账号");
        }
        if (request.ownerDisplayName() == null || request.ownerDisplayName().isBlank()) {
            throw BusinessException.badRequest("请输入商户主账号姓名");
        }
        if (request.ownerPhone() == null || request.ownerPhone().isBlank()) {
            throw BusinessException.badRequest("请输入商户主账号手机号");
        }
        if (request.ownerPassword() == null || request.ownerPassword().isBlank()) {
            throw BusinessException.badRequest("请输入商户主账号初始密码");
        }
    }

    private Merchant ensureMerchantExists(Long merchantId) {
        return merchantRepository.findById(merchantId).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
    }

    private Merchant ensureEnabledMerchant(Long merchantId) {
        var merchant = ensureMerchantExists(merchantId);
        if (merchant.status() != MerchantStatus.ENABLED) {
            throw BusinessException.badRequest("商户已停用");
        }
        return merchant;
    }

    private MerchantStore ensureStoreExists(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
    }

    private void addBlocker(List<String> blockers, String label, int count) {
        if (count > 0) {
            blockers.add(label + " " + count + " 条");
        }
    }

    private EmployeeResponse toEmployeeResponse(Account account) {
        var scopes = authQueryRepository.findStoreScopes(account.id());
        var authorizedStores = scopes.stream()
            .flatMap(scope -> {
                if (scope.storeId() == null) {
                    return storeRepository.findByMerchantId(scope.merchantId()).stream();
                }
                return storeRepository.findByIds(List.of(scope.storeId())).stream();
            })
            .map(this::toResponse)
            .toList();
        return new EmployeeResponse(
            account.id(),
            account.merchantId(),
            account.storeId(),
            account.username(),
            account.displayName(),
            account.phone(),
            account.accountType().name(),
            account.status().name(),
            authorizedStores
        );
    }

    private MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
            merchant.id(),
            merchant.merchantCode(),
            merchant.merchantName(),
            merchant.contactName(),
            merchant.contactPhone(),
            merchant.businessLicenseNo(),
            merchant.status().name()
        );
    }

    private StoreResponse toResponse(MerchantStore store) {
        return new StoreResponse(
            store.id(),
            store.merchantId(),
            store.storeCode(),
            store.storeName(),
            store.address(),
            store.businessHours(),
            store.longitude(),
            store.latitude(),
            store.qrContent(),
            store.status().name()
        );
    }

    private String nextCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String createStoreQrContent(String storeCode, String storeName) {
        if (!alipayProperties.miniAppQrcodeReady()) {
            return pendingStoreQrContent(storeCode);
        }
        return createOfficialStoreQrContent(storeCode, storeName);
    }

    private String createOfficialStoreQrContent(String storeCode, String storeName) {
        return alipayGatewayClient.createMiniAppStoreQrcode(storeCode, storeName).qrCodeUrl();
    }

    private String pendingStoreQrContent(String storeCode) {
        return "ALIPAY_QRCODE_PENDING:" + alipayProperties.getMiniAppStorePage() + "?storeCode=" + storeCode;
    }
}
