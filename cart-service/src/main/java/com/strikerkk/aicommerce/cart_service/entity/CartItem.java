package com.strikerkk.aicommerce.cart_service.entity;


import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(
    name = "cart_item",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_product_variant",
        columnNames = {"cart_id", "product_id", "variant_id"}
    )
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price_at_add", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtAdd;
}
