package com.xniu.rental.externalorder.service;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ExternalOrderRenewalBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderRenewalBackfillRunner.class);

    private final ExternalOrderAutoRenewalService renewalService;

    public ExternalOrderRenewalBackfillRunner(ExternalOrderAutoRenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var count = renewalService.accrueDueOrders(LocalDateTime.now());
        if (count > 0) {
            log.info("已补记 {} 个到期外部补录续租周期", count);
        }
    }
}
