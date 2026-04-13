package com.strikerkk.aicommerce.product_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "product_variant")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "size")
    private String size;

    @Column(name = "color")
    private String color;

    @Column(name = "stock_count", nullable = false)
    private Integer stockCount;

    @Column(name = "price_override", precision = 10, scale = 2)
    private BigDecimal priceOverride;
}
