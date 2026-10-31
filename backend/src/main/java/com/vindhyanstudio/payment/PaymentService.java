/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.customer.Customer;
import com.vindhyanstudio.order.Order;
import com.vindhyanstudio.order.OrderService;
import com.vindhyanstudio.order.OrderStatus;
import com.vindhyanstudio.order.PaymentMode;

/**
 * Gateway-independent payment flow. An order is confirmed only by a server-side proof
 * (signature, gateway status lookup or signed webhook) AND only if the paid amount matches.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final OrderService orders;

    public PaymentService(PaymentGateway gateway, PaymentRepository payments, OrderService orders) {
        this.gateway = gateway;
        this.payments = payments;
        this.orders = orders;
    }

    /** Everything the browser needs to open the gateway's checkout. */
    public record PaymentInit(String provider, String keyId, String mode, String providerOrderId, String sessionToken,
                              long amountPaise, String currency, Long orderId, String orderNo,
                              String name, String email, String phone) {}

    @Transactional
    public PaymentInit start(Order order, Customer customer) {
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) throw ApiException.badRequest("This order is already " + order.getStatus());
        String phone = customer.getPhone() != null ? customer.getPhone() : order.address().phone();
        String name = customer.getName() != null ? customer.getName() : order.address().name();
        PaymentGateway.ProviderOrder po;
        try {
            po = gateway.createOrder(order.getOrderNo(), order.getTotal(),
                    new PaymentGateway.Payer(String.valueOf(customer.getId()), name, customer.getEmail(), phone));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gateway order creation failed for {}", order.getOrderNo(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment gateway is not reachable. Please retry in a moment.");
        }
        payments.save(new Payment(order.getId(), gateway.name(), po.providerOrderId(), order.getTotal()));
        PaymentGateway.ClientConfig client = gateway.clientConfig();
        return new PaymentInit(gateway.name(), client.keyId(), client.mode(), po.providerOrderId(), po.sessionToken(),
                RazorpayPaymentGateway.paise(order.getTotal()), "INR", order.getId(), order.getOrderNo(), name, customer.getEmail(), phone);
    }

    /** Browser says "paid": confirm with the gateway before trusting it. */
    @Transactional
    public Long verify(long customerId, String providerOrderId, String providerPaymentId, String signature) {
        Payment p = payments.findFirstByProviderOrderId(providerOrderId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        Order o = orders.get(p.getOrderId());
        if (!o.getCustomerId().equals(customerId)) throw ApiException.forbidden("Not your order");
        PaymentGateway.Verification v;
        try {
            v = gateway.verify(providerOrderId, providerPaymentId, signature);
        } catch (Exception e) {
            log.error("Payment verification call failed for {}", providerOrderId, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't confirm the payment yet. If money was deducted, your order will be confirmed automatically within a few minutes.");
        }
        if (!v.paid()) {
            p.markFailed(providerPaymentId);
            throw ApiException.badRequest("Payment could not be verified. If money was deducted it will be confirmed automatically or refunded.");
        }
        markPaid(p, v.paymentId() != null ? v.paymentId() : providerPaymentId, v.amountPaise(), "payment");
        return o.getId();
    }

    /** Signed webhook from the active gateway. */
    @Transactional
    public boolean onWebhook(String rawBody, Map<String, String> headers) {
        Optional<PaymentGateway.WebhookEvent> event = gateway.parseWebhook(rawBody, headers);
        if (event.isEmpty()) return false;
        PaymentGateway.WebhookEvent e = event.get();
        payments.findFirstByProviderOrderId(e.providerOrderId()).ifPresent(p -> {
            if (e.success()) markPaid(p, e.paymentId(), e.amountPaise(), "webhook");
            else p.markFailed(e.paymentId());
        });
        return true;
    }

    /**
     * Safety net for missed webhooks / closed tabs: every 5 minutes ask the gateway about
     * unfinished attempts from the last 2 days.
     */
    @Scheduled(fixedDelayString = "${app.payment.reconcile-interval-ms:300000}", initialDelay = 60000)
    public void reconcile() {
        if (gateway instanceof MockPaymentGateway || gateway instanceof DisabledPaymentGateway) return;
        OffsetDateTime now = OffsetDateTime.now();
        List<Payment> open = payments.findByStatusInAndCreatedAtBetween(
                List.of(Payment.Status.CREATED, Payment.Status.FAILED), now.minusDays(2), now.minusMinutes(3));
        for (Payment p : open) {
            try {
                gateway.fetchStatus(p.getProviderOrderId()).filter(PaymentGateway.Verification::paid)
                        .ifPresent(v -> reconcileOne(p.getId(), v));
            } catch (Exception e) {
                log.warn("Reconcile check failed for {}: {}", p.getProviderOrderId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void reconcileOne(Long paymentId, PaymentGateway.Verification v) {
        payments.findById(paymentId).ifPresent(p -> markPaid(p, v.paymentId(), v.amountPaise(), "reconcile"));
    }

    /** Refunds a paid online order (called when the owner cancels it). Returns a note for the timeline, or null. */
    @Transactional
    public String refundIfPaid(Long orderId) {
        Order o = orders.get(orderId);
        if (o.getPaymentMode() != PaymentMode.ONLINE) return null;
        Payment paid = payments.findByOrderIdOrderByIdDesc(orderId).stream()
                .filter(p -> p.getStatus() == Payment.Status.PAID).findFirst().orElse(null);
        if (paid == null) return null;
        try {
            String refundId = gateway.refund(paid.getProviderOrderId(), paid.getProviderPaymentId(), paid.getAmount(), "RF-" + o.getOrderNo());
            paid.markRefunded(refundId);
            String note = "Refund of Rs." + paid.getAmount() + " started (" + gateway.name() + " " + refundId + "), usually 5–7 working days";
            orders.addNote(orderId, note, "payment");
            return note;
        } catch (Exception e) {
            log.error("Refund failed for {}", o.getOrderNo(), e);
            String note = "Automatic refund FAILED. Refund manually from the " + gateway.name() + " dashboard (" + e.getMessage() + ")";
            orders.addNote(orderId, note, "payment");
            return note;
        }
    }

    private void markPaid(Payment p, String providerPaymentId, Long amountPaise, String source) {
        if (p.getStatus() == Payment.Status.PAID || p.getStatus() == Payment.Status.REFUNDED) return;
        long expected = RazorpayPaymentGateway.paise(p.getAmount());
        if (amountPaise != null && amountPaise != expected) {
            log.error("Amount mismatch on {}: paid {} paise, expected {}", p.getProviderOrderId(), amountPaise, expected);
            p.markFailed(providerPaymentId);
            orders.addNote(p.getOrderId(), "Payment amount mismatch (paid " + amountPaise / 100.0 + ", expected " + p.getAmount()
                    + "). Not confirmed; check the gateway dashboard.", "payment");
            return;
        }
        p.markPaid(providerPaymentId);
        orders.confirm(p.getOrderId(), "Paid online (" + p.getProvider() + " " + (providerPaymentId == null ? "" : providerPaymentId) + ", via " + source + ")", "payment");
    }
}
