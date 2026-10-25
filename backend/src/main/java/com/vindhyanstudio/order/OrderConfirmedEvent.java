package com.vindhyanstudio.order;

/** Published inside the confirming transaction; handled after commit by {@link FulfilmentListener}. */
public record OrderConfirmedEvent(Long orderId) {}
