package com.strikerkk.aicommerce.product_service.consumer;

import com.strikerkk.aicommerce.order_service.event.OrderConfirmedEvent;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConfirmedConsumer {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;


    @Transactional
    @KafkaListener(topics = "order-confirmed-topic")
    public void handleOrderConfirmedEvent (OrderConfirmedEvent event) {

        List<ProductVariant> variantsToUpdate = new ArrayList<>();
        Set<Long> affectedProductIds = new HashSet<>();

        event.getOrderPlacedItems()
                .forEach(orderPlacedItem -> {
                    ProductVariant variant = productVariantRepository
                            .findByIdAndProductId(orderPlacedItem.getVariantId(),orderPlacedItem.getProductId())
                            .orElse(null);

                    if(variant == null) {
                        log.error("Variant not found for productId: {}, variantId: {}",
                                orderPlacedItem.getProductId(),
                                orderPlacedItem.getVariantId());
                        return;
                    };

                    variant.setStockCount(variant.getStockCount() - orderPlacedItem.getQuantity());
                    variantsToUpdate.add(variant);
                    affectedProductIds.add(orderPlacedItem.getProductId());

                    log.info("Queuing stock update for productId: {}, variantId: {}", orderPlacedItem.getProductId(), orderPlacedItem.getVariantId());
                });

        // Single batch write
        productVariantRepository.saveAll(variantsToUpdate);
        updateProductStockCount(affectedProductIds);
    }

    private void updateProductStockCount(Set<Long> productIds) {
        List<Product> productsToUpdate = new ArrayList<>();

        for(Long productId : productIds) {
            List<ProductVariant> variants = productVariantRepository.findByProductId(productId);

            if(variants.isEmpty()) continue;

            int stockCount = variants
                    .stream()
                    .mapToInt(ProductVariant::getStockCount)
                    .sum();

            Product product = variants.getFirst().getProduct();
            product.setStockCount(stockCount);
            productsToUpdate.add(product);

            log.info("Queuing total stock update for productId: {} to {}", productId, stockCount);
        }

        // Single batch write
        productRepository.saveAll(productsToUpdate);
    }

}
