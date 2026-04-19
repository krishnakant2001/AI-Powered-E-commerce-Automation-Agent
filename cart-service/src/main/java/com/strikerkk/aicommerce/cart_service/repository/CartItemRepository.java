package com.strikerkk.aicommerce.cart_service.repository;

import com.strikerkk.aicommerce.cart_service.entity.CartItem;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndProductIdAndVariantId(
            Long id,
            Long productId,
            Long variantId
    );
}
