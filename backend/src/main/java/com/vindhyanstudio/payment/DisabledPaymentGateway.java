package com.vindhyanstudio.payment;

import java.math.BigDecimal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.vindhyanstudio.common.ApiException;

/**
 * PAYMENT_PROVIDER=disabled: the live site takes cash-on-delivery orders only, until a real
 * gateway (Razorpay) is approved. Never use "mock" on a public site; it lets anyone "pay" for free.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "disabled")
public class DisabledPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "disabled";
    }

    @Override
    public String createProviderOrder(String receipt, BigDecimal amount) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Online payment is coming soon. Please choose cash on delivery.");
    }

    @Override
    public String publicKey() {
        return null;
    }

    @Override
    public boolean verifyPayment(String providerOrderId, String providerPaymentId, String signature) {
        return false;
    }

    @Override
    public boolean verifyWebhook(String body, String signature) {
        return false;
    }
}
