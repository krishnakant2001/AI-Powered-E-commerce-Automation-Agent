package com.strikerkk.aicommerce.product_service.repository;

import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByIdAndProductId(Long variantId, Long productId);

}
