package com.xniu.rental.externalorder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Reconciles still-mutable historical renewal rows after Flyway migrations. */
@Component
@Order(15)
public class ExternalOrderRenewalReconciliationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderRenewalReconciliationRunner.class);

    private final ExternalOrderAutoRenewalService renewalService;

    public ExternalOrderRenewalReconciliationRunner(ExternalOrderAutoRenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var count = renewalService.reconcileAllPendingEvents();
        if (count > 0) {
            log.info("已按系统续租基准及核销修改时间重算 {} 个仍可修改的补录续租周期", count);
        }
    }
}
