package com.strikerkk.aicommerce.payment_service.repository;

import com.strikerkk.aicommerce.payment_service.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findAllByPaymentId(Long paymentId);
}
