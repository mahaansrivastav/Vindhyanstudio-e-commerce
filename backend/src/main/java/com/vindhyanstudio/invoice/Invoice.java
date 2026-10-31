/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.invoice;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "invoices")
public class Invoice {

    public enum Type { INVOICE, CREDIT_NOTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private String invoiceNo;
    @Enumerated(EnumType.STRING)
    private Type type;
    private String storageKey;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Invoice() {}

    Invoice(Long orderId, String invoiceNo, Type type, String storageKey) {
        this.orderId = orderId;
        this.invoiceNo = invoiceNo;
        this.type = type;
        this.storageKey = storageKey;
    }

    public Long getOrderId() { return orderId; }
    public String getInvoiceNo() { return invoiceNo; }
    public Type getType() { return type; }
    public String getStorageKey() { return storageKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
