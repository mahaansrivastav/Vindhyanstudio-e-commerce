/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long orderId;
    String channel;
    String recipient;
    String subject;
    String status;
    String error;
    OffsetDateTime createdAt = OffsetDateTime.now();

    protected NotificationLog() {}

    NotificationLog(Message m, String status, String error) {
        this.orderId = m.orderId();
        this.channel = m.channel().name();
        this.recipient = m.to();
        this.subject = m.subject();
        this.status = status;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }
}
