package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.vindhyanstudio.common.Json;
import com.vindhyanstudio.config.AppProperties;

import tools.jackson.databind.JsonNode;

/**
 * Cashfree Payments (PG v3 + JS SDK popup). Docs: https://www.cashfree.com/docs/payments/online/web/redirect
 * Env: PAYMENT_PROVIDER=cashfree, CASHFREE_APP_ID, CASHFREE_SECRET_KEY, CASHFREE_ENV=sandbox|production.
 * Webhook: {APP_PUBLIC_BASE_URL}/api/webhooks/payments (signed with the secret key).
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "cashfree")
public class CashfreePaymentGateway implements PaymentGateway {

    static final String API_VERSION = "2023-08-01";

    private final AppProperties.Payment.Cashfree cfg;
    private final RestClient http;
    private final boolean production;

    public CashfreePaymentGateway(AppProperties props) {
        this.cfg = props.payment().cashfree();
        if (blank(cfg.appId()) || blank(cfg.secretKey())) {
            throw new IllegalStateException("CASHFREE_APP_ID and CASHFREE_SECRET_KEY must be set when PAYMENT_PROVIDER=cashfree");
        }
        this.production = "production".equalsIgnoreCase(cfg.environment());
        this.http = RestClient.builder()
                .baseUrl(production ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg")
                .defaultHeader("x-client-id", cfg.appId())
                .defaultHeader("x-client-secret", cfg.secretKey())
                .defaultHeader("x-api-version", API_VERSION)
                .build();
    }

    @Override
    public String name() {
        return "cashfree";
    }

    @Override
    public ProviderOrder createOrder(String receipt, BigDecimal amount, Payer payer) {
        // Cashfree order ids must be unique per attempt, so retries get a suffix.
        String orderId = receipt + "-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customer_id", "cust_" + payer.customerId());
        customer.put("customer_phone", payer.phone() == null ? "9999999999" : payer.phone());
        if (payer.email() != null) customer.put("customer_email", payer.email());
        if (payer.name() != null) customer.put("customer_name", payer.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", orderId);
        body.put("order_amount", amount);
        body.put("order_currency", "INR");
        body.put("customer_details", customer);
        body.put("order_note", "Order " + receipt);
        JsonNode res = http.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        return new ProviderOrder(res.path("order_id").asString(), res.path("payment_session_id").asString());
    }

    @Override
    public ClientConfig clientConfig() {
        return new ClientConfig(null, production ? "production" : "sandbox");
    }

    /** Cashfree has no client-side signature; the server asks Cashfree for the order status. */
    @Override
    public Verification verify(String providerOrderId, String providerPaymentId, String signature) {
        return fetchStatus(providerOrderId).orElse(new Verification(false, null, null));
    }

    @Override
    public Optional<Verification> fetchStatus(String providerOrderId) {
        JsonNode order = http.get().uri("/orders/{id}", providerOrderId).retrieve().body(JsonNode.class);
        if (!"PAID".equals(order.path("order_status").asString())) return Optional.empty();
        String paymentId = null;
        JsonNode payments = http.get().uri("/orders/{id}/payments", providerOrderId).retrieve().body(JsonNode.class);
        for (JsonNode p : payments) {
            if ("SUCCESS".equals(p.path("payment_status").asString())) paymentId = p.path("cf_payment_id").asString();
        }
        return Optional.of(new Verification(true, paymentId, RazorpayPaymentGateway.paise(order.path("order_amount").decimalValue())));
    }

    @Override
    public Optional<WebhookEvent> parseWebhook(String rawBody, Map<String, String> headers) {
        String signature = headers.get("x-webhook-signature");
        String timestamp = headers.get("x-webhook-timestamp");
        if (signature == null || timestamp == null) return Optional.empty();
        String expected = hmacBase64(timestamp + rawBody, cfg.secretKey());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) return Optional.empty();
        JsonNode root = Json.mapper().readTree(rawBody);
        String type = root.path("type").asString();
        JsonNode data = root.path("data");
        String orderId = data.path("order").path("order_id").asString();
        if (orderId.isEmpty()) return Optional.empty();
        boolean success = type.equals("PAYMENT_SUCCESS_WEBHOOK");
        boolean failed = type.equals("PAYMENT_FAILED_WEBHOOK") || type.equals("PAYMENT_USER_DROPPED_WEBHOOK");
        if (!success && !failed) return Optional.empty();
        JsonNode payment = data.path("payment");
        return Optional.of(new WebhookEvent(orderId, payment.path("cf_payment_id").asString(), success,
                RazorpayPaymentGateway.paise(payment.path("payment_amount").decimalValue())));
    }

    @Override
    public String refund(String providerOrderId, String providerPaymentId, BigDecimal amount, String refundRef) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refund_amount", amount);
        body.put("refund_id", refundRef.replaceAll("[^A-Za-z0-9_-]", ""));
        body.put("refund_note", "Order cancelled");
        JsonNode res = http.post().uri("/orders/{id}/refunds", providerOrderId)
                .header("x-idempotency-key", UUID.nameUUIDFromBytes(refundRef.getBytes(StandardCharsets.UTF_8)).toString())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        return res.path("cf_refund_id").asString();
    }

    private static String hmacBase64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
