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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConfimedConsumer {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;


    @Transactional
    @KafkaListener(topics = "order-confirmed-topic")
    public void handleOrderConfirmedEvent (OrderConfirmedEvent event) {

        Set<Long> productSet = new HashSet<>();

        event.getOrderPlacedItems()
                .forEach(orderPlacedItem -> {
                    ProductVariant productVariant = productVariantRepository
                            .findByIdAndProductId(orderPlacedItem.getVariantId(),orderPlacedItem.getProductId())
                            .orElse(null);

                    if(productVariant == null) {
                        log.error("Variant not found for productId: {}, variantId: {}",
                                orderPlacedItem.getProductId(),
                                orderPlacedItem.getVariantId());
                        return;
                    };

                    productSet.add(orderPlacedItem.getProductId());

                    log.info("Updating stock for productId: {}, variantId: {}", orderPlacedItem.getProductId(), orderPlacedItem.getVariantId());

                    productVariant.setStockCount(productVariant.getStockCount() - orderPlacedItem.getQuantity());
                    productVariantRepository.save(productVariant);

                });

        updateProductStockCount(productSet);
    }

    private void updateProductStockCount(Set<Long> productSet) {
        for(Long productId : productSet) {
            List<ProductVariant> variants = productVariantRepository.findByProductId(productId);

            if(variants.isEmpty()) continue;

            int stockCount = variants
                    .stream()
                    .mapToInt(ProductVariant::getStockCount)
                    .sum();

            Product product = variants.getFirst().getProduct();
            product.setStockCount(stockCount);
            productRepository.save(product);

            log.info("Updated total stock for productId: {} to {}", productId, stockCount);
        }
    }

}
