package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fake gateway for local/dev. The storefront shows a "Simulate payment" dialog; sending
 * signature "fail" simulates a failed payment.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String createProviderOrder(String receipt, BigDecimal amount) {
        return "mock_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    @Override
    public String publicKey() {
        return null;
    }

    @Override
    public boolean verifyPayment(String providerOrderId, String providerPaymentId, String signature) {
        return !"fail".equals(signature);
    }

    @Override
    public boolean verifyWebhook(String body, String signature) {
        return false;
    }
}
