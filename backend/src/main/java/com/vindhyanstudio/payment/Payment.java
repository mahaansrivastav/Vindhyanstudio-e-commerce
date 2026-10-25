package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class Payment {

    public enum Status { CREATED, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private String provider;
    private String providerOrderId;
    private String providerPaymentId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private Status status = Status.CREATED;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Payment() {}

    Payment(Long orderId, String provider, String providerOrderId, BigDecimal amount) {
        this.orderId = orderId;
        this.provider = provider;
        this.providerOrderId = providerOrderId;
        this.amount = amount;
    }

    public Long getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Status getStatus() { return status; }

    void markPaid(String providerPaymentId) {
        this.status = Status.PAID;
        this.providerPaymentId = providerPaymentId;
    }

    void markFailed(String providerPaymentId) {
        if (status != Status.PAID) this.status = Status.FAILED;
        if (providerPaymentId != null) this.providerPaymentId = providerPaymentId;
    }
}
