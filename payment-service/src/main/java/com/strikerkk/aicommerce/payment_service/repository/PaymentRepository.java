package com.strikerkk.aicommerce.payment_service.repository;

import com.strikerkk.aicommerce.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByGatewayOrderId(String razorpayOrderId);
}
