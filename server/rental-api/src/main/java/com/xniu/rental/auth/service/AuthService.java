package com.xniu.rental.auth.service;

import com.xniu.rental.auth.dto.AlipayLoginRequest;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.LoginResponse;
import com.xniu.rental.auth.dto.PasswordLoginRequest;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.model.Account;
import com.xniu.rental.auth.model.AccountStatus;
import com.xniu.rental.auth.model.AccountType;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.repository.AuthQueryRepository;
import com.xniu.rental.auth.repository.SessionRepository;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int TOKEN_BYTES = 48;
    private static final long SESSION_HOURS = 24 * 14;

    private final AccountRepository accountRepository;
    private final AuthQueryRepository authQueryRepository;
    private final SessionRepository sessionRepository;
    private final PasswordHasher passwordHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        AccountRepository accountRepository,
        AuthQueryRepository authQueryRepository,
        SessionRepository sessionRepository,
        PasswordHasher passwordHasher
    ) {
        this.accountRepository = accountRepository;
        this.authQueryRepository = authQueryRepository;
        this.sessionRepository = sessionRepository;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public LoginResponse adminLogin(PasswordLoginRequest request) {
        var account = passwordLogin(request);
        if (account.accountType() != AccountType.PLATFORM_ADMIN
            && account.accountType() != AccountType.FINANCE
            && account.accountType() != AccountType.INVESTOR) {
            throw BusinessException.forbidden("当前账号不能登录后台");
        }
        return createLogin(account);
    }

    @Transactional
    public LoginResponse merchantLogin(PasswordLoginRequest request) {
        var account = passwordLogin(request);
        if (account.accountType() != AccountType.MERCHANT_OWNER
            && account.accountType() != AccountType.STORE_MANAGER
            && account.accountType() != AccountType.STORE_OPERATOR
            && account.accountType() != AccountType.STORE_STAFF
            && account.accountType() != AccountType.MAINTENANCE_STAFF
            && account.accountType() != AccountType.WAREHOUSE_STAFF) {
            throw BusinessException.forbidden("当前账号不是商户账号");
        }
        return createLogin(account);
    }

    @Transactional
    public LoginResponse alipayLogin(AlipayLoginRequest request) {
        var alipayUserId = resolveAlipayUserId(request.authCode());
        var displayName = request.nickName() == null || request.nickName().isBlank()
            ? "支付宝用户"
            : request.nickName();
        var account = accountRepository.findByAlipayUserId(alipayUserId)
            .orElseGet(() -> accountRepository.createAlipayConsumer(alipayUserId, displayName, request.phone()));
        ensureEnabled(account);
        return createLogin(account);
    }

    public CurrentAccount authenticate(String token) {
        var now = LocalDateTime.now();
        var session = sessionRepository.findByToken(token)
            .orElseThrow(() -> BusinessException.unauthorized("登录已失效"));
        if (!session.isActive(now)) {
            throw BusinessException.unauthorized("登录已过期");
        }
        var account = accountRepository.findById(session.accountId())
            .orElseThrow(() -> BusinessException.unauthorized("账号不存在"));
        ensureEnabled(account);
        return new CurrentAccount(token, buildCurrentAccount(account));
    }

    public CurrentAccountResponse current(CurrentAccount currentAccount) {
        if (currentAccount == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return currentAccount.account();
    }

    @Transactional
    public LoginResponse refresh(CurrentAccount currentAccount) {
        if (currentAccount == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        sessionRepository.revoke(currentAccount.token(), LocalDateTime.now());
        var account = accountRepository.findById(currentAccount.account().id())
            .orElseThrow(() -> BusinessException.unauthorized("账号不存在"));
        ensureEnabled(account);
        return createLogin(account);
    }

    @Transactional
    public void logout(CurrentAccount currentAccount) {
        if (currentAccount == null) {
            return;
        }
        sessionRepository.revoke(currentAccount.token(), LocalDateTime.now());
    }

    private Account passwordLogin(PasswordLoginRequest request) {
        var account = accountRepository.findByUsername(request.username())
            .orElseThrow(() -> BusinessException.unauthorized("账号或密码错误"));
        ensureEnabled(account);
        if (!passwordHasher.matches(request.password(), account.passwordHash())) {
            throw BusinessException.unauthorized("账号或密码错误");
        }
        return account;
    }

    private LoginResponse createLogin(Account account) {
        var now = LocalDateTime.now();
        var expiresAt = now.plusHours(SESSION_HOURS);
        var token = generateToken();
        sessionRepository.create(token, account.id(), account.accountType(), expiresAt);
        accountRepository.markLastLoginAt(account.id(), now);
        return new LoginResponse(token, expiresAt, buildCurrentAccount(account));
    }

    private CurrentAccountResponse buildCurrentAccount(Account account) {
        var storeScopes = authQueryRepository.findStoreScopes(account.id()).stream()
            .map(scope -> new StoreScopeResponse(
                scope.merchantId(),
                scope.storeId(),
                scope.scopeType().name()
            ))
            .toList();
        return new CurrentAccountResponse(
            account.id(),
            account.accountType().name(),
            account.username(),
            account.phone(),
            account.alipayUserId(),
            account.displayName(),
            account.merchantId(),
            account.storeId(),
            account.investorId(),
            authQueryRepository.findRoleCodes(account.id()),
            authQueryRepository.findPermissionCodes(account.id()),
            storeScopes
        );
    }

    private void ensureEnabled(Account account) {
        if (account.status() != AccountStatus.ENABLED) {
            throw BusinessException.forbidden("账号已停用");
        }
    }

    private String generateToken() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resolveAlipayUserId(String authCode) {
        return "alipay_" + Math.abs(authCode.hashCode());
    }
}
