package com.vindhyanstudio.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class Payment {

    public enum Status { CREATED, PAID, FAILED, REFUNDED }

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
    private String refundId;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt;

    protected Payment() {}

    Payment(Long orderId, String provider, String providerOrderId, BigDecimal amount) {
        this.orderId = orderId;
        this.provider = provider;
        this.providerOrderId = providerOrderId;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getRefundId() { return refundId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void markPaid(String providerPaymentId) {
        this.status = Status.PAID;
        if (providerPaymentId != null && !providerPaymentId.isBlank()) this.providerPaymentId = providerPaymentId;
        this.updatedAt = OffsetDateTime.now();
    }

    void markFailed(String providerPaymentId) {
        if (status == Status.CREATED) this.status = Status.FAILED;
        if (providerPaymentId != null && !providerPaymentId.isBlank() && this.providerPaymentId == null) this.providerPaymentId = providerPaymentId;
        this.updatedAt = OffsetDateTime.now();
    }

    void markRefunded(String refundId) {
        this.status = Status.REFUNDED;
        this.refundId = refundId;
        this.updatedAt = OffsetDateTime.now();
    }
}
