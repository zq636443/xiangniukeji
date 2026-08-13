package com.xniu.rental.job;

import com.xniu.rental.externalorder.service.ExternalOrderAutoRenewalService;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExternalOrderRenewalScheduleJob {

    private final ExternalOrderAutoRenewalService renewalService;

    public ExternalOrderRenewalScheduleJob(ExternalOrderAutoRenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @Scheduled(cron = "${xniu.external-order-renewal.cron:0 5 * * * *}")
    public void accrueDueRenewals() {
        renewalService.accrueDueOrders(LocalDateTime.now());
    }
}
