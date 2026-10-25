package com.vindhyanstudio.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerIdAndDeletedFalseOrderByIdAsc(Long customerId);

    Optional<Address> findByIdAndCustomerIdAndDeletedFalse(Long id, Long customerId);
}
