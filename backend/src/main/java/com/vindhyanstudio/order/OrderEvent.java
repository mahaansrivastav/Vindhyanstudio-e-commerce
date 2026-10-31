/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.order;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private String note;
    private String actor;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected OrderEvent() {}

    OrderEvent(OrderStatus status, String note, String actor) {
        this.status = status;
        this.note = note == null ? null : note.substring(0, Math.min(note.length(), 500));
        this.actor = actor;
    }

    public OrderStatus getStatus() { return status; }
    public String getNote() { return note; }
    public String getActor() { return actor; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
