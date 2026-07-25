package com.xniu.rental.investor.service;

import com.xniu.rental.auth.model.Account;
import com.xniu.rental.auth.model.AccountType;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.service.PasswordHasher;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.investor.dto.InvestorRequest;
import com.xniu.rental.investor.dto.InvestorResponse;
import com.xniu.rental.investor.model.Investor;
import com.xniu.rental.investor.model.InvestorStatus;
import com.xniu.rental.investor.repository.InvestorRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestorService {

    private final InvestorRepository investorRepository;
    private final AuthorizationService authorizationService;
    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    public InvestorService(
        InvestorRepository investorRepository,
        AuthorizationService authorizationService,
        AccountRepository accountRepository,
        PasswordHasher passwordHasher
    ) {
        this.investorRepository = investorRepository;
        this.authorizationService = authorizationService;
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
    }

    public List<InvestorResponse> listInvestors(String keyword) {
        authorizationService.requirePermission("investor.read");
        return investorRepository.list(keyword).stream().map(this::toResponse).toList();
    }

    @Transactional
    public InvestorResponse createInvestor(InvestorRequest request) {
        authorizationService.requirePermission("investor.write");
        var investor = investorRepository.create(
            nextCode(),
            request.investorName(),
            request.contactName(),
            request.contactPhone()
        );
        if (Boolean.TRUE.equals(request.createAccount())) {
            validateAccountRequest(request);
            createInvestorAccount(investor.id(), request.username(), request.displayName(), request.phone(), request.password());
        }
        return toResponse(investor);
    }

    @Transactional
    public InvestorResponse updateInvestor(Long id, InvestorRequest request) {
        authorizationService.requirePermission("investor.write");
        ensureInvestorExists(id);
        return toResponse(investorRepository.update(
            id,
            request.investorName(),
            request.contactName(),
            request.contactPhone()
        ));
    }

    @Transactional
    public InvestorResponse updateInvestorStatus(Long id, InvestorStatus status) {
        authorizationService.requirePermission("investor.write");
        ensureInvestorExists(id);
        return toResponse(investorRepository.updateStatus(id, status));
    }

    @Transactional
    public void deleteInvestor(Long id) {
        authorizationService.requirePermission("investor.write");
        ensureInvestorExists(id);
        if (investorRepository.countReferences(id) > 0) {
            throw BusinessException.badRequest("出资方已关联账号、资产或结算数据，不能删除");
        }
        investorRepository.delete(id);
    }

    public Investor ensureInvestorExists(Long id) {
        return investorRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("出资方不存在"));
    }

    private void validateAccountRequest(InvestorRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw BusinessException.badRequest("请输入出资方账号登录账号");
        }
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw BusinessException.badRequest("请输入出资方账号显示名称");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw BusinessException.badRequest("请输入出资方账号手机号");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw BusinessException.badRequest("请输入出资方账号初始密码");
        }
    }

    private Account createInvestorAccount(Long investorId, String username, String displayName, String phone, String password) {
        if (accountRepository.findByUsername(username).isPresent()) {
            throw BusinessException.badRequest("账号已存在");
        }
        var account = accountRepository.createManual(
            AccountType.INVESTOR,
            username,
            phone,
            displayName,
            passwordHasher.encode(password),
            null,
            null,
            investorId
        );
        accountRepository.bindRole(account.id(), AccountType.INVESTOR.name());
        return account;
    }

    private InvestorResponse toResponse(Investor investor) {
        return new InvestorResponse(
            investor.id(),
            investor.investorCode(),
            investor.investorName(),
            investor.contactName(),
            investor.contactPhone(),
            investor.status().name()
        );
    }

    private String nextCode() {
        return "I-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
