package com.vindhyanstudio.invoice;

import jakarta.persistence.*;

@Entity
@Table(name = "invoice_counters")
class InvoiceCounter {

    @Id
    String fiscalYear;
    int lastNo;

    protected InvoiceCounter() {}

    InvoiceCounter(String key) {
        this.fiscalYear = key;
    }
}
