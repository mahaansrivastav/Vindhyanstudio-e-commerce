package com.vindhyanstudio.order;

public record OrderStatusChangedEvent(Long orderId, OrderStatus status) {}
