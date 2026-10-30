package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * An online payment provider. Implementations: {@link RazorpayPaymentGateway}, {@link CashfreePaymentGateway},
 * {@link MockPaymentGateway} (local only), {@link DisabledPaymentGateway} (COD-only shop).
 * Exactly one is active, chosen by PAYMENT_PROVIDER. Adding another gateway = one new class.
 */
public interface PaymentGateway {

    String name();

    /** Who is paying; some gateways (Cashfree) require phone/email on the order. */
    record Payer(String customerId, String name, String email, String phone) {}

    /** providerOrderId = gateway's order id; sessionToken = what the browser SDK needs (Cashfree), null for Razorpay. */
    record ProviderOrder(String providerOrderId, String sessionToken) {}

    /** Public, non-secret values the browser checkout needs. mode = "sandbox"/"production" (Cashfree) or null. */
    record ClientConfig(String keyId, String mode) {}

    /** Result of checking a payment. amountPaise may be null if the gateway didn't report it. */
    record Verification(boolean paid, String paymentId, Long amountPaise) {}

    /** A trusted (signature-checked) payment event from a webhook. */
    record WebhookEvent(String providerOrderId, String paymentId, boolean success, Long amountPaise) {}

    ProviderOrder createOrder(String receipt, BigDecimal amount, Payer payer) throws Exception;

    ClientConfig clientConfig();

    /** Called after the browser reports success. Must not trust the browser: check signature or ask the gateway. */
    Verification verify(String providerOrderId, String providerPaymentId, String signature) throws Exception;

    /** Asks the gateway for the order's current state (used by the reconciliation job). */
    Optional<Verification> fetchStatus(String providerOrderId) throws Exception;

    /** Verifies the webhook signature and extracts the payment event; empty if invalid or irrelevant. */
    Optional<WebhookEvent> parseWebhook(String rawBody, Map<String, String> headers);

    /** Full or partial refund; returns the gateway's refund id. */
    String refund(String providerOrderId, String providerPaymentId, BigDecimal amount, String refundRef) throws Exception;
}
