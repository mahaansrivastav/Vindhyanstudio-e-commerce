package com.vindhyanstudio.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. CONFIRMED = paid online or COD accepted. PRINTING/READY are set by the owner;
 * DISPATCHED/DELIVERED/RTO come from the owner or the shipping webhook.
 */
public enum OrderStatus {
    PAYMENT_PENDING, CONFIRMED, PRINTING, READY, DISPATCHED, DELIVERED, CANCELLED, RTO;

    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            PAYMENT_PENDING, EnumSet.of(CANCELLED),
            CONFIRMED, EnumSet.of(PRINTING, READY, CANCELLED),
            PRINTING, EnumSet.of(READY, CANCELLED),
            READY, EnumSet.of(DISPATCHED, CANCELLED),
            DISPATCHED, EnumSet.of(DELIVERED, RTO),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            RTO, EnumSet.noneOf(OrderStatus.class));

    public boolean canMoveTo(OrderStatus next) {
        return NEXT.get(this).contains(next);
    }

    public Set<OrderStatus> next() {
        return NEXT.get(this);
    }
}
