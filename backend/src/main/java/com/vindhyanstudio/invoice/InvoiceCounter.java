/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
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
