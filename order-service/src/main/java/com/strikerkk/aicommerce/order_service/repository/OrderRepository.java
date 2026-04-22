package com.strikerkk.aicommerce.order_service.repository;

import com.strikerkk.aicommerce.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdAndUserId(Long orderId, Long userId);

    List<Order> findAllByUserId(Long userId);
}
