package com.vindhyanstudio.payment;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.customer.Customer;
import com.vindhyanstudio.order.Order;
import com.vindhyanstudio.order.OrderService;
import com.vindhyanstudio.order.OrderStatus;

@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final OrderService orders;

    public PaymentService(PaymentGateway gateway, PaymentRepository payments, OrderService orders) {
        this.gateway = gateway;
        this.payments = payments;
        this.orders = orders;
    }

    /** Everything the browser needs to open the payment popup. */
    public record PaymentInit(String provider, String keyId, String providerOrderId, long amountPaise, String currency,
                              Long orderId, String orderNo, String name, String email, String phone) {}

    @Transactional
    public PaymentInit start(Order order, Customer customer) {
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) throw ApiException.badRequest("This order is already " + order.getStatus());
        BigDecimal amount = order.getTotal();
        String providerOrderId;
        try {
            providerOrderId = gateway.createProviderOrder(order.getOrderNo(), amount);
        } catch (Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Payment gateway is not reachable. Please retry.");
        }
        payments.save(new Payment(order.getId(), gateway.name(), providerOrderId, amount));
        return new PaymentInit(gateway.name(), gateway.publicKey(), providerOrderId, amount.movePointRight(2).longValueExact(),
                "INR", order.getId(), order.getOrderNo(), customer.getName(), customer.getEmail(), customer.getPhone());
    }

    /** Called by the browser after the popup reports success. The order is only confirmed if the signature is valid. */
    @Transactional
    public Long verify(long customerId, String providerOrderId, String providerPaymentId, String signature) {
        Payment p = payments.findFirstByProviderOrderId(providerOrderId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        Order o = orders.get(p.getOrderId());
        if (!o.getCustomerId().equals(customerId)) throw ApiException.forbidden("Not your order");
        if (!gateway.verifyPayment(providerOrderId, providerPaymentId, signature)) {
            p.markFailed(providerPaymentId);
            throw ApiException.badRequest("Payment could not be verified. If money was deducted it will be confirmed automatically or refunded.");
        }
        markPaid(p, providerPaymentId);
        return o.getId();
    }

    /** Webhook path (source of truth for late successes, e.g. the customer closed the tab). */
    @Transactional
    public void onProviderEvent(String providerOrderId, String providerPaymentId, boolean success) {
        payments.findFirstByProviderOrderId(providerOrderId).ifPresent(p -> {
            if (success) markPaid(p, providerPaymentId); else p.markFailed(providerPaymentId);
        });
    }

    private void markPaid(Payment p, String providerPaymentId) {
        if (p.getStatus() == Payment.Status.PAID) return;
        p.markPaid(providerPaymentId);
        orders.confirm(p.getOrderId(), "Paid online (" + p.getProvider() + " " + providerPaymentId + ")", "payment");
    }
}
