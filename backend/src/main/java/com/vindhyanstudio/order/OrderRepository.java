package com.vindhyanstudio.order;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerIdOrderByIdDesc(Long customerId);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findTop200ByOrderByIdDesc();

    List<Order> findTop200ByStatusOrderByIdDesc(OrderStatus status);

    List<Order> findByCreatedAtAfter(OffsetDateTime after);

    long countByStatus(OrderStatus status);

    List<Order> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);
}
