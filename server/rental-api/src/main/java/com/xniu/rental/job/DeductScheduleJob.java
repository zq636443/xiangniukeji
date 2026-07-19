package com.xniu.rental.job;

import com.xniu.rental.pay.service.AgreementDeductService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeductScheduleJob {

    private final AgreementDeductService agreementDeductService;

    public DeductScheduleJob(AgreementDeductService agreementDeductService) {
        this.agreementDeductService = agreementDeductService;
    }

    @Scheduled(cron = "${xniu.deduct.cron:0 0 2 * * *}")
    public void runDailyDeduct() {
        agreementDeductService.runDueDeductInternal(50, "定时任务自动扣款");
    }
}
