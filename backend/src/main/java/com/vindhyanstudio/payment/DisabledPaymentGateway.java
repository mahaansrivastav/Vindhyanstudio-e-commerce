package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.vindhyanstudio.common.ApiException;

/**
 * PAYMENT_PROVIDER=disabled: the live site takes cash-on-delivery orders only, until a real
 * gateway is approved. Never use "mock" on a public site; it lets anyone "pay" for free.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "disabled")
public class DisabledPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "disabled";
    }

    @Override
    public ProviderOrder createOrder(String receipt, BigDecimal amount, Payer payer) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Online payment is coming soon. Please choose cash on delivery.");
    }

    @Override
    public ClientConfig clientConfig() {
        return new ClientConfig(null, null);
    }

    @Override
    public Verification verify(String providerOrderId, String providerPaymentId, String signature) {
        return new Verification(false, null, null);
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
        throw new IllegalStateException("Online payments are disabled");
    }
}
