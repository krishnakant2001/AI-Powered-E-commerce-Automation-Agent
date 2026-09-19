package com.strikerkk.aicommerce.product_service.consumer;

import com.strikerkk.aicommerce.order_service.event.OrderConfirmedEvent;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmedConsumer")
class OrderConfirmedConsumerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private OrderConfirmedConsumer consumer;

    @Captor
    private ArgumentCaptor<List<ProductVariant>> variantsCaptor;

    @Captor
    private ArgumentCaptor<List<Product>> productsCaptor;

    @Test
    @DisplayName("decrements the stock of every ordered variant")
    void shouldDecrementTheVariantStock() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant first = TestDataFactory.variant(100L, product, 10);
        ProductVariant second = TestDataFactory.variant(101L, product, 20);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(first));
        when(productVariantRepository.findByIdAndProductId(101L, 1L)).thenReturn(Optional.of(second));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(first, second));

        consumer.handleOrderConfirmedEvent(TestDataFactory.orderConfirmedEvent(
                TestDataFactory.orderPlacedItem(1L, 100L, 2),
                TestDataFactory.orderPlacedItem(1L, 101L, 3)));

        assertThat(first.getStockCount()).isEqualTo(8);
        assertThat(second.getStockCount()).isEqualTo(17);
    }

    @Test
    @DisplayName("writes every updated variant in a single batch")
    void shouldWriteTheVariantsInOneBatch() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant first = TestDataFactory.variant(100L, product, 10);
        ProductVariant second = TestDataFactory.variant(101L, product, 20);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(first));
        when(productVariantRepository.findByIdAndProductId(101L, 1L)).thenReturn(Optional.of(second));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(first, second));

        consumer.handleOrderConfirmedEvent(TestDataFactory.orderConfirmedEvent(
                TestDataFactory.orderPlacedItem(1L, 100L, 2),
                TestDataFactory.orderPlacedItem(1L, 101L, 3)));

        verify(productVariantRepository).saveAll(variantsCaptor.capture());
        assertThat(variantsCaptor.getValue()).containsExactly(first, second);
    }

    @Test
    @DisplayName("recomputes the aggregated stock of the product from all of its variants")
    void shouldRecomputeTheProductStock() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant ordered = TestDataFactory.variant(100L, product, 10);
        ProductVariant untouched = TestDataFactory.variant(101L, product, 5);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(ordered));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(ordered, untouched));

        consumer.handleOrderConfirmedEvent(
                TestDataFactory.orderConfirmedEvent(TestDataFactory.orderPlacedItem(1L, 100L, 4)));

        // 10 - 4 = 6 for the ordered variant, plus the 5 of the untouched one
        assertThat(product.getStockCount()).isEqualTo(11);

        verify(productRepository).saveAll(productsCaptor.capture());
        assertThat(productsCaptor.getValue()).containsExactly(product);
    }

    @Test
    @DisplayName("aggregates a product only once when several of its variants were ordered")
    void shouldAggregateEachProductOnlyOnce() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant first = TestDataFactory.variant(100L, product, 10);
        ProductVariant second = TestDataFactory.variant(101L, product, 10);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(first));
        when(productVariantRepository.findByIdAndProductId(101L, 1L)).thenReturn(Optional.of(second));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(first, second));

        consumer.handleOrderConfirmedEvent(TestDataFactory.orderConfirmedEvent(
                TestDataFactory.orderPlacedItem(1L, 100L, 1),
                TestDataFactory.orderPlacedItem(1L, 101L, 1)));

        verify(productVariantRepository).findByProductId(1L);
        verify(productRepository).saveAll(productsCaptor.capture());
        assertThat(productsCaptor.getValue()).containsExactly(product);
    }

    @Test
    @DisplayName("updates several products of the same order")
    void shouldUpdateSeveralProducts() {
        Product firstProduct = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        Product secondProduct = TestDataFactory.product(2L, TestDataFactory.OTHER_ADMIN_ID);
        ProductVariant firstVariant = TestDataFactory.variant(100L, firstProduct, 10);
        ProductVariant secondVariant = TestDataFactory.variant(200L, secondProduct, 30);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(firstVariant));
        when(productVariantRepository.findByIdAndProductId(200L, 2L)).thenReturn(Optional.of(secondVariant));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(firstVariant));
        when(productVariantRepository.findByProductId(2L)).thenReturn(List.of(secondVariant));

        consumer.handleOrderConfirmedEvent(TestDataFactory.orderConfirmedEvent(
                TestDataFactory.orderPlacedItem(1L, 100L, 1),
                TestDataFactory.orderPlacedItem(2L, 200L, 10)));

        assertThat(firstProduct.getStockCount()).isEqualTo(9);
        assertThat(secondProduct.getStockCount()).isEqualTo(20);

        verify(productRepository).saveAll(productsCaptor.capture());
        assertThat(productsCaptor.getValue()).containsExactlyInAnyOrder(firstProduct, secondProduct);
    }

    @Test
    @DisplayName("skips an unknown variant instead of failing the whole batch")
    void shouldSkipAnUnknownVariant() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant known = TestDataFactory.variant(100L, product, 10);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(known));
        when(productVariantRepository.findByIdAndProductId(999L, 1L)).thenReturn(Optional.empty());
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(known));

        consumer.handleOrderConfirmedEvent(TestDataFactory.orderConfirmedEvent(
                TestDataFactory.orderPlacedItem(1L, 100L, 2),
                TestDataFactory.orderPlacedItem(1L, 999L, 5)));

        assertThat(known.getStockCount()).isEqualTo(8);

        verify(productVariantRepository).saveAll(variantsCaptor.capture());
        assertThat(variantsCaptor.getValue()).containsExactly(known);
    }

    @Test
    @DisplayName("does not aggregate a product whose variants were all unknown")
    void shouldNotAggregateAProductWithoutResolvedVariants() {
        when(productVariantRepository.findByIdAndProductId(999L, 1L)).thenReturn(Optional.empty());

        consumer.handleOrderConfirmedEvent(
                TestDataFactory.orderConfirmedEvent(TestDataFactory.orderPlacedItem(1L, 999L, 5)));

        verify(productVariantRepository).saveAll(variantsCaptor.capture());
        assertThat(variantsCaptor.getValue()).isEmpty();

        verify(productVariantRepository, never()).findByProductId(anyLong());
        verify(productRepository).saveAll(productsCaptor.capture());
        assertThat(productsCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("skips the aggregation when the product suddenly has no variant left")
    void shouldSkipTheAggregationWithoutVariants() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant variant = TestDataFactory.variant(100L, product, 10);
        Integer stockBefore = product.getStockCount();

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(variant));
        when(productVariantRepository.findByProductId(1L)).thenReturn(Collections.emptyList());

        consumer.handleOrderConfirmedEvent(
                TestDataFactory.orderConfirmedEvent(TestDataFactory.orderPlacedItem(1L, 100L, 1)));

        assertThat(product.getStockCount()).isEqualTo(stockBefore);

        verify(productRepository).saveAll(productsCaptor.capture());
        assertThat(productsCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("handles an event that carries no item")
    void shouldHandleAnEmptyEvent() {
        OrderConfirmedEvent event = TestDataFactory.orderConfirmedEvent();

        consumer.handleOrderConfirmedEvent(event);

        verify(productVariantRepository).saveAll(variantsCaptor.capture());
        assertThat(variantsCaptor.getValue()).isEmpty();
        verify(productRepository).saveAll(productsCaptor.capture());
        assertThat(productsCaptor.getValue()).isEmpty();
        verify(productVariantRepository, never()).findByProductId(anyLong());
    }

    @Test
    @DisplayName("lets the stock reach exactly zero")
    void shouldLetTheStockReachZero() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant variant = TestDataFactory.variant(100L, product, 3);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(variant));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(variant));

        consumer.handleOrderConfirmedEvent(
                TestDataFactory.orderConfirmedEvent(TestDataFactory.orderPlacedItem(1L, 100L, 3)));

        assertThat(variant.getStockCount()).isZero();
        assertThat(product.getStockCount()).isZero();
    }

    @Test
    @DisplayName("does not guard against an oversell - the stock can go negative")
    void shouldNotGuardAgainstAnOversell() {
        Product product = TestDataFactory.product(1L, TestDataFactory.ADMIN_ID);
        ProductVariant variant = TestDataFactory.variant(100L, product, 2);

        when(productVariantRepository.findByIdAndProductId(100L, 1L)).thenReturn(Optional.of(variant));
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(variant));

        consumer.handleOrderConfirmedEvent(
                TestDataFactory.orderConfirmedEvent(TestDataFactory.orderPlacedItem(1L, 100L, 5)));

        // Documents the current behaviour: the consumer trusts the reservation made upstream.
        assertThat(variant.getStockCount()).isEqualTo(-3);
        assertThat(product.getStockCount()).isEqualTo(-3);
    }
}

