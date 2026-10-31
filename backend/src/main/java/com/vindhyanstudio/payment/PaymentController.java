/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.payment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vindhyanstudio.common.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    public record VerifyRequest(String providerOrderId, String providerPaymentId, String signature) {}

    @PostMapping("/api/payments/verify")
    Map<String, Object> verify(@RequestBody VerifyRequest body) {
        Long orderId = payments.verify(CurrentUser.id(), body.providerOrderId(), body.providerPaymentId(), body.signature());
        return Map.of("orderId", orderId);
    }

    /**
     * One webhook URL for whichever gateway is active (PAYMENT_PROVIDER). The raw body is kept
     * as-is because signatures are computed over the exact bytes. /razorpay is kept as an alias.
     */
    @PostMapping({"/api/webhooks/payments", "/api/webhooks/razorpay"})
    ResponseEntity<Void> webhook(@RequestBody String rawBody, HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) headers.put(name.toLowerCase(), request.getHeader(name));
        return payments.onWebhook(rawBody, headers) ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
    }
}
