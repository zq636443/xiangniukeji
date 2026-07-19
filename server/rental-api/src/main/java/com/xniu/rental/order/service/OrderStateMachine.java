package com.xniu.rental.order.service;

import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.model.OrderStatus;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMachine {

    private final Map<OrderStatus, Set<OrderStatus>> transitions = Map.ofEntries(
        Map.entry(OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.PENDING_REAL_NAME, OrderStatus.CANCELLED, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.PENDING_REAL_NAME, Set.of(OrderStatus.PENDING_AGREEMENT, OrderStatus.CANCELLED, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.PENDING_AGREEMENT, Set.of(OrderStatus.PENDING_DEPOSIT_AUTH, OrderStatus.CANCELLED, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.PENDING_DEPOSIT_AUTH, Set.of(OrderStatus.PENDING_VERIFY, OrderStatus.CANCELLED, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.PENDING_VERIFY, Set.of(OrderStatus.PENDING_PICKUP, OrderStatus.CANCELLED, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.PENDING_PICKUP, Set.of(OrderStatus.RENTING, OrderStatus.CANCELLED, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.RENTING, Set.of(OrderStatus.PENDING_RETURN, OrderStatus.OVERDUE, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.PENDING_RETURN, Set.of(OrderStatus.COMPLETED, OrderStatus.OVERDUE, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.OVERDUE, Set.of(OrderStatus.PENDING_SUPPLEMENT, OrderStatus.COMPLETED, OrderStatus.EXCEPTION)),
        Map.entry(OrderStatus.PENDING_SUPPLEMENT, Set.of(OrderStatus.RENTING, OrderStatus.COMPLETED, OrderStatus.EXCEPTION))
    );

    private final Set<OrderStatus> cancellable = Set.of(
        OrderStatus.PENDING_PAYMENT,
        OrderStatus.PENDING_REAL_NAME,
        OrderStatus.PENDING_AGREEMENT,
        OrderStatus.PENDING_DEPOSIT_AUTH,
        OrderStatus.PENDING_VERIFY,
        OrderStatus.PENDING_PICKUP
    );

    private final Set<OrderStatus> terminal = Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.EXCEPTION);

    public void assertCanTransit(OrderStatus current, OrderStatus target) {
        if (current == target) {
            throw BusinessException.badRequest("订单已处于目标状态");
        }
        if (!transitions.getOrDefault(current, Set.of()).contains(target)) {
            throw BusinessException.badRequest("订单状态不允许从 " + current + " 流转到 " + target);
        }
    }

    public void assertCanCancel(OrderStatus current) {
        if (!cancellable.contains(current)) {
            throw BusinessException.badRequest("当前订单状态不允许取消");
        }
    }

    public void assertCanMarkException(OrderStatus current) {
        if (terminal.contains(current)) {
            throw BusinessException.badRequest("终态订单不能标记异常");
        }
    }
}
