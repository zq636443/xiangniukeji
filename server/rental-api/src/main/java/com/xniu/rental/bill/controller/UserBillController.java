package com.xniu.rental.bill.controller;

import com.xniu.rental.bill.dto.BillResponse;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/bills")
public class UserBillController {

    private final BillService billService;

    public UserBillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping
    public ApiResponse<List<BillResponse>> listBills(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orderId
    ) {
        return ApiResponse.ok(billService.listUserBills(status, orderId));
    }
}
