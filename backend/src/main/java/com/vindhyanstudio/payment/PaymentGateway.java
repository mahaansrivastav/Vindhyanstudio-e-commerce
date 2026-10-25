package com.vindhyanstudio.payment;

import java.math.BigDecimal;

/**
 * An online payment provider. v1: {@link MockPaymentGateway} (local) and {@link RazorpayPaymentGateway}.
 * Selected with app.payment.provider. To add Cashfree/PhonePe/Stripe, implement this interface.
 */
public interface PaymentGateway {

    String name();

    /** Creates the provider-side order the browser will pay against. */
    String createProviderOrder(String receipt, BigDecimal amount) throws Exception;

    /** Public key the browser checkout needs (null if not applicable). */
    String publicKey();

    /** Checks the signature the browser got back after a successful payment. */
    boolean verifyPayment(String providerOrderId, String providerPaymentId, String signature);

    /** Checks a webhook body against its signature header. */
    boolean verifyWebhook(String body, String signature);
}
