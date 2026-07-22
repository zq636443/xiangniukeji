package com.xniu.rental.auth.service;

import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.dto.SystemAccountCreateRequest;
import com.xniu.rental.auth.dto.SystemAccountPermissionUpdateRequest;
import com.xniu.rental.auth.dto.SystemAccountResetPasswordRequest;
import com.xniu.rental.auth.dto.SystemAccountResponse;
import com.xniu.rental.auth.dto.SystemAccountRoleUpdateRequest;
import com.xniu.rental.auth.dto.SystemAccountScopeUpdateRequest;
import com.xniu.rental.auth.dto.SystemAccountUpdateRequest;
import com.xniu.rental.auth.dto.SystemPermissionResponse;
import com.xniu.rental.auth.dto.SystemRoleResponse;
import com.xniu.rental.auth.model.AccountStatus;
import com.xniu.rental.auth.model.AccountType;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.repository.AuthQueryRepository;
import com.xniu.rental.auth.repository.SystemManagementRepository;
import com.xniu.rental.auth.repository.SystemManagementRepository.AccountRow;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.investor.repository.InvestorRepository;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemManagementService {

    private static final Set<String> PLATFORM_ROLE_CODES = Set.of("PLATFORM_ADMIN", "FINANCE");
    private static final Set<String> MERCHANT_ROLE_CODES = Set.of("MERCHANT_OWNER", "STORE_MANAGER", "STORE_OPERATOR", "STORE_STAFF", "MAINTENANCE_STAFF", "WAREHOUSE_STAFF");
    private static final Set<String> INVESTOR_ROLE_CODES = Set.of("INVESTOR");
    private static final Set<String> CONSUMER_ROLE_CODES = Set.of("CONSUMER");
    private static final Set<String> DIRECT_ASSIGNABLE_PERMISSION_CODES = Set.of("order.create");

    private final SystemManagementRepository systemManagementRepository;
    private final AccountRepository accountRepository;
    private final AuthQueryRepository authQueryRepository;
    private final MerchantRepository merchantRepository;
    private final InvestorRepository investorRepository;
    private final StoreRepository storeRepository;
    private final PasswordHasher passwordHasher;

    public SystemManagementService(
        SystemManagementRepository systemManagementRepository,
        AccountRepository accountRepository,
        AuthQueryRepository authQueryRepository,
        MerchantRepository merchantRepository,
        InvestorRepository investorRepository,
        StoreRepository storeRepository,
        PasswordHasher passwordHasher
    ) {
        this.systemManagementRepository = systemManagementRepository;
        this.accountRepository = accountRepository;
        this.authQueryRepository = authQueryRepository;
        this.merchantRepository = merchantRepository;
        this.investorRepository = investorRepository;
        this.storeRepository = storeRepository;
        this.passwordHasher = passwordHasher;
    }

    public List<SystemAccountResponse> listAccounts(String keyword, String accountType, Long merchantId, String status) {
        return systemManagementRepository.listAccounts(keyword, parseAccountTypeNullable(accountType), merchantId, parseStatusNullable(status)).stream()
            .map(this::toAccountResponse)
            .toList();
    }

    public List<SystemRoleResponse> listRoles(String status) {
        return systemManagementRepository.listRoles(status).stream()
            .map(role -> new SystemRoleResponse(
                role.id(),
                role.roleCode(),
                role.roleName(),
                role.roleScope(),
                role.status(),
                role.createdAt(),
                systemManagementRepository.findPermissionCodesByRole(role.id())
            ))
            .toList();
    }

    public List<SystemPermissionResponse> listPermissions(String moduleCode) {
        return systemManagementRepository.listPermissions(moduleCode).stream()
            .map(permission -> new SystemPermissionResponse(
                permission.id(),
                permission.permissionCode(),
                permission.permissionName(),
                permission.moduleCode(),
                permission.createdAt()
            ))
            .toList();
    }

    public SystemAccountResponse getAccount(Long accountId) {
        return toAccountResponse(ensureAccount(accountId));
    }

    @Transactional
    public SystemAccountResponse createAccount(SystemAccountCreateRequest request) {
        if (accountRepository.findByUsername(request.username()).isPresent()) {
            throw BusinessException.badRequest("账号已存在");
        }
        var role = systemManagementRepository.findRoleByCode(request.roleCode())
            .orElseThrow(() -> BusinessException.badRequest("角色不存在"));
        var accountType = AccountType.valueOf(role.roleCode());
        validateCreateRequest(request, accountType);
        var storeIds = normalizeStoreIds(request.merchantId(), accountType, request.storeIds());
        var account = accountRepository.createManual(
            accountType,
            request.username(),
            request.phone(),
            request.displayName(),
            passwordHasher.encode(request.password()),
            request.merchantId(),
            storeIds.isEmpty() ? null : storeIds.getFirst(),
            request.investorId()
        );
        accountRepository.bindRole(account.id(), role.roleCode());
        if (request.merchantId() != null) {
            if (accountType == AccountType.MERCHANT_OWNER) {
                systemManagementRepository.insertAllMerchantScope(account.id(), request.merchantId());
            } else {
                for (var storeId : storeIds) {
                    systemManagementRepository.insertSingleStoreScope(account.id(), request.merchantId(), storeId);
                }
            }
        }
        return toAccountResponse(ensureAccount(account.id()));
    }

    @Transactional
    public SystemAccountResponse updateAccount(Long accountId, SystemAccountUpdateRequest request) {
        var account = ensureAccount(accountId);
        var username = normalizeUsername(account, request.username());
        var displayName = request.displayName().trim();
        var phone = request.phone().trim();
        if (!usernameEquals(account.username(), username) && accountRepository.findByUsername(username).isPresent()) {
            throw BusinessException.badRequest("账号已存在");
        }
        accountRepository.updateBasicInfo(accountId, username, phone, displayName);
        return toAccountResponse(ensureAccount(accountId));
    }

    @Transactional
    public SystemAccountResponse resetPassword(Long accountId, SystemAccountResetPasswordRequest request) {
        var account = ensureAccount(accountId);
        if (account.username() == null || account.username().isBlank()) {
            throw BusinessException.badRequest("消费者账号不支持后台重置密码");
        }
        accountRepository.updatePassword(accountId, passwordHasher.encode(request.password().trim()));
        return toAccountResponse(ensureAccount(accountId));
    }

    @Transactional
    public SystemAccountResponse updateAccountStatus(Long accountId, String status) {
        var account = ensureAccount(accountId);
        systemManagementRepository.updateAccountStatus(accountId, parseStatus(status));
        return toAccountResponse(ensureAccount(accountId));
    }

    @Transactional
    public SystemAccountResponse updateAccountRole(Long accountId, SystemAccountRoleUpdateRequest request) {
        var account = ensureAccount(accountId);
        var role = systemManagementRepository.findRoleByCode(request.roleCode())
            .orElseThrow(() -> BusinessException.badRequest("角色不存在"));
        validateRoleChange(account, role.roleCode());
        systemManagementRepository.replaceAccountRole(accountId, role.roleCode());
        systemManagementRepository.updateAccountType(accountId, AccountType.valueOf(role.roleCode()));
        if ("MERCHANT_OWNER".equals(role.roleCode())) {
            resetAllStoreScopes(account);
        }
        return toAccountResponse(ensureAccount(accountId));
    }

    @Transactional
    public SystemAccountResponse updateAccountScopes(Long accountId, SystemAccountScopeUpdateRequest request) {
        var account = ensureAccount(accountId);
        if (account.merchantId() == null) {
            throw BusinessException.badRequest("只有商户体系账号可以配置门店范围");
        }
        var roleCodes = authQueryRepository.findRoleCodes(accountId);
        if (roleCodes.isEmpty()) {
            throw BusinessException.badRequest("账号未绑定角色");
        }
        var roleCode = roleCodes.getFirst();
        if ("MERCHANT_OWNER".equals(roleCode)) {
            resetAllStoreScopes(account);
            return toAccountResponse(ensureAccount(accountId));
        }
        var storeIds = request.storeIds() == null ? List.<Long>of() : request.storeIds().stream().distinct().toList();
        if (storeIds.isEmpty()) {
            throw BusinessException.badRequest("请至少选择一个门店");
        }
        var stores = storeRepository.findByIds(storeIds);
        if (stores.size() != storeIds.size() || stores.stream().anyMatch(store -> !store.merchantId().equals(account.merchantId()))) {
            throw BusinessException.badRequest("门店不属于当前商户");
        }
        if (stores.stream().anyMatch(store -> store.status() != StoreStatus.ENABLED)) {
            throw BusinessException.badRequest("停用门店不能授权给账号");
        }
        systemManagementRepository.clearStoreScopes(accountId);
        for (var storeId : storeIds) {
            systemManagementRepository.insertSingleStoreScope(accountId, account.merchantId(), storeId);
        }
        systemManagementRepository.updateDefaultStore(accountId, storeIds.getFirst());
        return toAccountResponse(ensureAccount(accountId));
    }

    @Transactional
    public SystemAccountResponse updateAccountPermissions(Long accountId, SystemAccountPermissionUpdateRequest request) {
        var account = ensureAccount(accountId);
        if (account.merchantId() == null) {
            throw BusinessException.badRequest("只有商户体系账号可以配置新建订单权限");
        }
        var permissionCodes = request.permissionCodes().stream().distinct().toList();
        if (permissionCodes.stream().anyMatch(code -> !DIRECT_ASSIGNABLE_PERMISSION_CODES.contains(code))) {
            throw BusinessException.badRequest("包含不可直接分配的权限");
        }
        systemManagementRepository.replaceDirectPermissions(accountId, permissionCodes);
        return toAccountResponse(ensureAccount(accountId));
    }

    private void resetAllStoreScopes(AccountRow account) {
        systemManagementRepository.clearStoreScopes(account.id());
        systemManagementRepository.insertAllMerchantScope(account.id(), account.merchantId());
        systemManagementRepository.updateDefaultStore(account.id(), null);
    }

    private void validateRoleChange(AccountRow account, String roleCode) {
        if (account.merchantId() != null) {
            if (!MERCHANT_ROLE_CODES.contains(roleCode)) {
                throw BusinessException.badRequest("商户体系账号只能调整为商户侧角色");
            }
            return;
        }
        if (account.investorId() != null) {
            if (!INVESTOR_ROLE_CODES.contains(roleCode)) {
                throw BusinessException.badRequest("出资方账号只能调整为出资方角色");
            }
            return;
        }
        if (account.username() == null || account.username().isBlank()) {
            if (!CONSUMER_ROLE_CODES.contains(roleCode)) {
                throw BusinessException.badRequest("消费者账号只能保留消费者角色");
            }
            return;
        }
        if (!PLATFORM_ROLE_CODES.contains(roleCode)) {
            throw BusinessException.badRequest("平台账号只能调整为平台侧角色");
        }
    }

    private void validateCreateRequest(SystemAccountCreateRequest request, AccountType accountType) {
        if (MERCHANT_ROLE_CODES.contains(accountType.name())) {
            if (request.merchantId() == null) {
                throw BusinessException.badRequest("商户侧账号必须绑定所属商户");
            }
            var merchant = merchantRepository.findById(request.merchantId()).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
            if (merchant.status() != MerchantStatus.ENABLED) {
                throw BusinessException.badRequest("停用商户不能新增账号");
            }
            return;
        }
        if (INVESTOR_ROLE_CODES.contains(accountType.name())) {
            if (request.investorId() == null) {
                throw BusinessException.badRequest("出资方账号必须绑定出资方");
            }
            investorRepository.findById(request.investorId()).orElseThrow(() -> BusinessException.badRequest("出资方不存在"));
            return;
        }
        if (CONSUMER_ROLE_CODES.contains(accountType.name())) {
            throw BusinessException.badRequest("消费者账号不支持后台手动创建");
        }
        if (request.merchantId() != null || request.investorId() != null) {
            throw BusinessException.badRequest("平台账号不能绑定商户或出资方");
        }
    }

    private List<Long> normalizeStoreIds(Long merchantId, AccountType accountType, List<Long> storeIds) {
        if (merchantId == null) {
            return List.of();
        }
        if (accountType == AccountType.MERCHANT_OWNER) {
            return List.of();
        }
        var result = storeIds == null ? List.<Long>of() : storeIds.stream().distinct().toList();
        if (result.isEmpty()) {
            throw BusinessException.badRequest("非商户老板账号必须至少选择一个门店");
        }
        var stores = storeRepository.findByIds(result);
        if (stores.size() != result.size() || stores.stream().anyMatch(store -> !store.merchantId().equals(merchantId))) {
            throw BusinessException.badRequest("门店不属于所选商户");
        }
        if (stores.stream().anyMatch(store -> store.status() != StoreStatus.ENABLED)) {
            throw BusinessException.badRequest("停用门店不能授权给账号");
        }
        return result;
    }

    private String normalizeUsername(AccountRow account, String username) {
        if (account.username() == null || account.username().isBlank()) {
            if (username != null && !username.isBlank()) {
                throw BusinessException.badRequest("消费者账号不支持修改登录账号");
            }
            return null;
        }
        if (username == null || username.isBlank()) {
            throw BusinessException.badRequest("请输入登录账号");
        }
        return username.trim();
    }

    private boolean usernameEquals(String left, String right) {
        return (left == null ? "" : left).equals(right == null ? "" : right);
    }

    private SystemAccountResponse toAccountResponse(AccountRow row) {
        return new SystemAccountResponse(
            row.id(),
            row.accountType().name(),
            row.username(),
            row.phone(),
            row.displayName(),
            row.merchantId(),
            row.merchantName(),
            row.storeId(),
            row.storeName(),
            row.investorId(),
            row.investorName(),
            row.status().name(),
            row.lastLoginAt(),
            row.createdAt(),
            authQueryRepository.findRoleCodes(row.id()),
            authQueryRepository.findPermissionCodes(row.id()),
            systemManagementRepository.findDirectPermissionCodes(row.id()),
            authQueryRepository.findStoreScopes(row.id()).stream()
                .map(scope -> new StoreScopeResponse(scope.merchantId(), scope.storeId(), scope.scopeType().name()))
                .toList()
        );
    }

    private AccountRow ensureAccount(Long accountId) {
        return systemManagementRepository.findAccount(accountId)
            .orElseThrow(() -> BusinessException.badRequest("账号不存在"));
    }

    private AccountType parseAccountTypeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AccountType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的账号类型");
        }
    }

    private AccountStatus parseStatusNullable(String value) {
        return value == null || value.isBlank() ? null : parseStatus(value);
    }

    private AccountStatus parseStatus(String value) {
        try {
            return AccountStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的账号状态");
        }
    }
}
