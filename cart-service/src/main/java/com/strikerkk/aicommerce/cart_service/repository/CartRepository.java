package com.strikerkk.aicommerce.cart_service.repository;

import com.strikerkk.aicommerce.cart_service.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
}
