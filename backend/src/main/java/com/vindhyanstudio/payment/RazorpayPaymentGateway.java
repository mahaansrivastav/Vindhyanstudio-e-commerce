package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
 * Razorpay Standard Checkout. Docs: https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/
 * Env: PAYMENT_PROVIDER=razorpay, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET.
 * Test keys start with rzp_test_, live keys with rzp_live_; the same code handles both.
 * Webhook: {APP_PUBLIC_BASE_URL}/api/webhooks/payments, events payment.captured, payment.failed, order.paid.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {

    private final AppProperties.Payment.Razorpay cfg;
    private final RestClient http;

    public RazorpayPaymentGateway(AppProperties props) {
        this.cfg = props.payment().razorpay();
        if (isBlank(cfg.keyId()) || isBlank(cfg.keySecret())) {
            throw new IllegalStateException("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set when PAYMENT_PROVIDER=razorpay");
        }
        String basic = Base64.getEncoder().encodeToString((cfg.keyId() + ":" + cfg.keySecret()).getBytes(StandardCharsets.UTF_8));
        this.http = RestClient.builder()
                .baseUrl("https://api.razorpay.com/v1")
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public ProviderOrder createOrder(String receipt, BigDecimal amount, Payer payer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", paise(amount));
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", Map.of("order_no", receipt));
        JsonNode res = http.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        return new ProviderOrder(res.path("id").asString(), null);
    }

    @Override
    public ClientConfig clientConfig() {
        return new ClientConfig(cfg.keyId(), null);
    }

    @Override
    public Verification verify(String providerOrderId, String providerPaymentId, String signature) {
        boolean ok = signature != null && providerPaymentId != null
                && constantTimeEquals(hmacHex(providerOrderId + "|" + providerPaymentId, cfg.keySecret()), signature);
        // The signature proves Razorpay authorised this payment for this order; amount was fixed at order creation.
        return new Verification(ok, providerPaymentId, null);
    }

    @Override
    public Optional<Verification> fetchStatus(String providerOrderId) {
        JsonNode res = http.get().uri("/orders/{id}/payments", providerOrderId).retrieve().body(JsonNode.class);
        for (JsonNode p : res.path("items")) {
            if ("captured".equals(p.path("status").asString())) {
                return Optional.of(new Verification(true, p.path("id").asString(), p.path("amount").asLong()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<WebhookEvent> parseWebhook(String rawBody, Map<String, String> headers) {
        String signature = headers.get("x-razorpay-signature");
        if (isBlank(cfg.webhookSecret()) || signature == null) return Optional.empty();
        if (!constantTimeEquals(hmacHex(rawBody, cfg.webhookSecret()), signature)) return Optional.empty();
        JsonNode root = Json.mapper().readTree(rawBody);
        String event = root.path("event").asString();
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String orderId = payment.path("order_id").asString();
        if (orderId.isEmpty()) return Optional.empty();
        boolean success = event.equals("payment.captured") || event.equals("order.paid");
        boolean failed = event.equals("payment.failed");
        if (!success && !failed) return Optional.empty();
        return Optional.of(new WebhookEvent(orderId, payment.path("id").asString(), success, payment.path("amount").asLong()));
    }

    @Override
    public String refund(String providerOrderId, String providerPaymentId, BigDecimal amount, String refundRef) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (amount != null) body.put("amount", paise(amount));
        body.put("speed", "normal");
        body.put("receipt", refundRef); // idempotency: a repeated receipt for the same payment is rejected
        JsonNode res = http.post().uri("/payments/{id}/refund", providerPaymentId)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        return res.path("id").asString();
    }

    static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private static String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
