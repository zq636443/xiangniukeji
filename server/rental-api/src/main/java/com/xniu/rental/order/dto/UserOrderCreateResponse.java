package com.xniu.rental.order.dto;

import com.xniu.rental.bill.dto.BillResponse;
import java.util.List;

public record UserOrderCreateResponse(
    OrderResponse order,
    List<BillResponse> bills
) {
}
