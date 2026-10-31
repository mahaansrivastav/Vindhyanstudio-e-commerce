/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerIdAndDeletedFalseOrderByIdAsc(Long customerId);

    Optional<Address> findByIdAndCustomerIdAndDeletedFalse(Long id, Long customerId);
}
