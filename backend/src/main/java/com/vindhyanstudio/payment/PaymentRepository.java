package com.vindhyanstudio.payment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByProviderOrderId(String providerOrderId);

    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);
}
