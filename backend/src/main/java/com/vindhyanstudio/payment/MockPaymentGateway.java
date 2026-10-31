/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fake gateway for LOCAL development only. The storefront shows a "Simulate payment" dialog;
 * signature "fail" simulates a failed payment. Never use on a public site.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ProviderOrder createOrder(String receipt, BigDecimal amount, Payer payer) {
        return new ProviderOrder("mock_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14), null);
    }

    @Override
    public ClientConfig clientConfig() {
        return new ClientConfig(null, null);
    }

    @Override
    public Verification verify(String providerOrderId, String providerPaymentId, String signature) {
        return new Verification(!"fail".equals(signature), providerPaymentId, null);
    }

    @Override
    public Optional<Verification> fetchStatus(String providerOrderId) {
        return Optional.empty();
    }

    @Override
    public Optional<WebhookEvent> parseWebhook(String rawBody, Map<String, String> headers) {
        return Optional.empty();
    }

    @Override
    public String refund(String providerOrderId, String providerPaymentId, BigDecimal amount, String refundRef) {
        return "mock_refund_" + refundRef;
    }
}
