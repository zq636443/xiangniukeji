package com.xniu.rental.externalorder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class ExternalOrderSettlementBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderSettlementBackfillRunner.class);

    private final ExternalRentalOrderService externalRentalOrderService;

    public ExternalOrderSettlementBackfillRunner(ExternalRentalOrderService externalRentalOrderService) {
        this.externalRentalOrderService = externalRentalOrderService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var count = externalRentalOrderService.backfillMissingSettlements();
        if (count > 0) {
            log.info("已为 {} 条历史补录订单补建分润快照和收益流水", count);
        }
    }
}
