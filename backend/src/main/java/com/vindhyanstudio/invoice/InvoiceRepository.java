package com.vindhyanstudio.invoice;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderIdAndType(Long orderId, Invoice.Type type);
}
