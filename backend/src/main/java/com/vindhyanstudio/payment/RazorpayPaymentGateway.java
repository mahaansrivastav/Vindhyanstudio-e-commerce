package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.vindhyanstudio.config.AppProperties;

/**
 * Razorpay Orders API + Checkout.js. Docs: https://razorpay.com/docs/payments/server-integration/
 * Env: PAYMENT_PROVIDER=razorpay, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET.
 * Webhook URL to register in the dashboard: {APP_PUBLIC_BASE_URL}/api/webhooks/razorpay
 * (events: payment.captured, payment.failed).
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
    @SuppressWarnings("unchecked")
    public String createProviderOrder(String receipt, BigDecimal amount) {
        long paise = amount.movePointRight(2).longValueExact();
        Map<String, Object> res = http.post().uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", paise, "currency", "INR", "receipt", receipt))
                .retrieve()
                .body(Map.class);
        return (String) res.get("id");
    }

    @Override
    public String publicKey() {
        return cfg.keyId();
    }

    @Override
    public boolean verifyPayment(String providerOrderId, String providerPaymentId, String signature) {
        if (signature == null) return false;
        return constantTimeEquals(hmac(providerOrderId + "|" + providerPaymentId, cfg.keySecret()), signature);
    }

    @Override
    public boolean verifyWebhook(String body, String signature) {
        if (isBlank(cfg.webhookSecret()) || signature == null) return false;
        return constantTimeEquals(hmac(body, cfg.webhookSecret()), signature);
    }

    private static String hmac(String data, String secret) {
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
