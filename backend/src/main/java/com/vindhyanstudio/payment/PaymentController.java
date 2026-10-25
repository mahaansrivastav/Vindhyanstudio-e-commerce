package com.vindhyanstudio.payment;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vindhyanstudio.common.CurrentUser;
import com.vindhyanstudio.common.Json;

import tools.jackson.databind.JsonNode;

@RestController
public class PaymentController {

    private final PaymentService payments;
    private final PaymentGateway gateway;

    public PaymentController(PaymentService payments, PaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    public record VerifyRequest(String providerOrderId, String providerPaymentId, String signature) {}

    @PostMapping("/api/payments/verify")
    Map<String, Object> verify(@RequestBody VerifyRequest body) {
        Long orderId = payments.verify(CurrentUser.id(), body.providerOrderId(), body.providerPaymentId(), body.signature());
        return Map.of("orderId", orderId);
    }

    @PostMapping("/api/webhooks/razorpay")
    ResponseEntity<Void> razorpayWebhook(@RequestBody String body,
                                         @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        if (!gateway.verifyWebhook(body, signature)) return ResponseEntity.status(401).build();
        JsonNode root = Json.mapper().readTree(body);
        String event = root.path("event").asString();
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String orderId = entity.path("order_id").asString();
        String paymentId = entity.path("id").asString();
        if (!orderId.isEmpty()) {
            if (event.equals("payment.captured") || event.equals("order.paid")) payments.onProviderEvent(orderId, paymentId, true);
            if (event.equals("payment.failed")) payments.onProviderEvent(orderId, paymentId, false);
        }
        return ResponseEntity.ok().build();
    }
}
