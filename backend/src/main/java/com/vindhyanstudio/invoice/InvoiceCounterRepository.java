package com.vindhyanstudio.invoice;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InvoiceCounter c where c.fiscalYear = :key")
    Optional<InvoiceCounter> lock(@Param("key") String key);
}
