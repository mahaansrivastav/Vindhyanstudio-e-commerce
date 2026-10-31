/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.vindhyanstudio.config.AppProperties;

/** Signature checks for each gateway, using the algorithms from their docs. No network calls. */
class GatewaySignatureTest {

    private static AppProperties props(AppProperties.Payment payment) {
        return new AppProperties(null, null, null, null, null, null, null, null, null, payment, null, null);
    }

    private static byte[] hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void razorpayCheckoutSignature() throws Exception {
        var gw = new RazorpayPaymentGateway(props(new AppProperties.Payment("razorpay", true,
                new AppProperties.Payment.Razorpay("rzp_test_x", "secret123", "whsec"), null)));
        String sig = HexFormat.of().formatHex(hmac("order_1|pay_1", "secret123"));
        assertTrue(gw.verify("order_1", "pay_1", sig).paid());
        assertFalse(gw.verify("order_1", "pay_2", sig).paid(), "signature for another payment must fail");
        assertFalse(gw.verify("order_1", "pay_1", "bogus").paid());
    }

    @Test
    void razorpayWebhook() throws Exception {
        var gw = new RazorpayPaymentGateway(props(new AppProperties.Payment("razorpay", true,
                new AppProperties.Payment.Razorpay("rzp_test_x", "secret123", "whsec"), null)));
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_9\",\"order_id\":\"order_9\",\"amount\":27800}}}}";
        String sig = HexFormat.of().formatHex(hmac(body, "whsec"));
        var e = gw.parseWebhook(body, Map.of("x-razorpay-signature", sig)).orElseThrow();
        assertEquals("order_9", e.providerOrderId());
        assertEquals("pay_9", e.paymentId());
        assertTrue(e.success());
        assertEquals(27800L, e.amountPaise());
        assertTrue(gw.parseWebhook(body.replace("27800", "100"), Map.of("x-razorpay-signature", sig)).isEmpty(), "tampered body must be rejected");
    }

    @Test
    void cashfreeWebhook() throws Exception {
        var gw = new CashfreePaymentGateway(props(new AppProperties.Payment("cashfree", true, null,
                new AppProperties.Payment.Cashfree("app", "cfsecret", "sandbox"))));
        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{\"order\":{\"order_id\":\"VS10001-ab12cd\"},"
                + "\"payment\":{\"cf_payment_id\":\"555\",\"payment_status\":\"SUCCESS\",\"payment_amount\":278.00}}}";
        String ts = "1730000000";
        String sig = Base64.getEncoder().encodeToString(hmac(ts + body, "cfsecret"));
        var e = gw.parseWebhook(body, Map.of("x-webhook-signature", sig, "x-webhook-timestamp", ts)).orElseThrow();
        assertEquals("VS10001-ab12cd", e.providerOrderId());
        assertEquals("555", e.paymentId());
        assertTrue(e.success());
        assertEquals(27800L, e.amountPaise());
        assertTrue(gw.parseWebhook(body, Map.of("x-webhook-signature", sig, "x-webhook-timestamp", "1730000001")).isEmpty());
        assertEquals("sandbox", gw.clientConfig().mode());
    }
}
