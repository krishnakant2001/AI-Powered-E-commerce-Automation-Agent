package com.strikerkk.aicommerce.payment_service.repository;

import com.strikerkk.aicommerce.payment_service.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
}
