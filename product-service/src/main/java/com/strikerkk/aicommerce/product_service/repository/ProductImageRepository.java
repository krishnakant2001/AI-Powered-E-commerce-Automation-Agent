package com.strikerkk.aicommerce.product_service.repository;

import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByIdAndProductId(Long imageId, Long productId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE ProductImage pi
        SET pi.isPrimary = false
        WHERE pi.product.id = :productId
    """)
    void restPrimaryImages(Long productId);
}
