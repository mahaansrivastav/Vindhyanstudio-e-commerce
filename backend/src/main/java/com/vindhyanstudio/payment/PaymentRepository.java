/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.payment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByProviderOrderId(String providerOrderId);

    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

    /** Unfinished attempts in a time window: candidates for reconciliation. */
    List<Payment> findByStatusInAndCreatedAtBetween(List<Payment.Status> statuses, OffsetDateTime from, OffsetDateTime to);
}
