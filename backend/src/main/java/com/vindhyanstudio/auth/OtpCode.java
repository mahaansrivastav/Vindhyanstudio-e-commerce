/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.auth;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "otp_codes")
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String identity;
    String codeHash;
    OffsetDateTime expiresAt;
    int attempts;
    boolean consumed;
    OffsetDateTime createdAt = OffsetDateTime.now();
}
