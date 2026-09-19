package com.strikerkk.aicommerce.cart_service.service;

import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import com.strikerkk.aicommerce.cart_service.support.TestDataFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartResilience4j")
class CartResilience4jTest {

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;

    private static final String INSTANCE = "product-service-call";

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private CartResilience4j cartResilience4j;

    private Method itemDetailsMethod() throws Exception {
        return CartResilience4j.class.getMethod("getItemDetails", Long.class, Long.class);
    }

    // delegation ------------------------------------------------------------------

    @Test
    @DisplayName("delegates straight to the Feign client")
    void delegatesToTheFeignClient() {
        ProductCartResponse expected = TestDataFactory.productCartResponse();
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenReturn(expected);

        assertThat(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).isSameAs(expected);
        verify(productClient).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("passes the ids through in the right order")
    void passesTheIdsInOrder() {
        when(productClient.getProductItemDetails(7L, 8L)).thenReturn(TestDataFactory.productCartResponse());

        cartResilience4j.getItemDetails(7L, 8L);

        verify(productClient).getProductItemDetails(7L, 8L);
    }

    @Test
    @DisplayName("adds no logic of its own - a null answer is returned as is")
    void addsNoLogic() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenReturn(null);

        assertThat(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).isNull();
    }

    // the fallback ------------------------------------------------------------------

    @Test
    @DisplayName("the fallback rethrows a RuntimeException that names the culprit")
    void theFallbackRethrows() {
        Throwable cause = new IllegalStateException("connection refused");

        assertThatThrownBy(() -> cartResilience4j.getItemDetailsFallback(PRODUCT_ID, VARIANT_ID, cause))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product Service unavailable")
                .hasCause(cause);
    }

    @Test
    @DisplayName("the fallback never returns a placeholder payload")
    void theFallbackNeverReturnsAPlaceholder() throws Exception {
        assertThat(CartResilience4j.class
                .getMethod("getItemDetailsFallback", Long.class, Long.class, Throwable.class)
                .getReturnType())
                .isEqualTo(ProductCartResponse.class);

        assertThatThrownBy(() -> cartResilience4j.getItemDetailsFallback(1L, 2L, new RuntimeException("x")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("the fallback tolerates a cause without a message")
    void theFallbackToleratesACauseWithoutAMessage() {
        assertThatThrownBy(() -> cartResilience4j.getItemDetailsFallback(1L, 2L, new RuntimeException()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product Service unavailable");
    }

    // annotation metadata ------------------------------------------------------------------

    @Test
    @DisplayName("is guarded by the 'product-service-call' retry instance")
    void isGuardedByTheRetryInstance() throws Exception {
        Retry retry = itemDetailsMethod().getAnnotation(Retry.class);

        assertThat(retry).isNotNull();
        assertThat(retry.name()).isEqualTo(INSTANCE);
    }

    @Test
    @DisplayName("is guarded by the 'product-service-call' circuit breaker instance")
    void isGuardedByTheCircuitBreakerInstance() throws Exception {
        CircuitBreaker circuitBreaker = itemDetailsMethod().getAnnotation(CircuitBreaker.class);

        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.name()).isEqualTo(INSTANCE);
    }

    @Test
    @DisplayName("the circuit breaker points at an existing fallback with the right signature")
    void theFallbackSignatureMatches() throws Exception {
        CircuitBreaker circuitBreaker = itemDetailsMethod().getAnnotation(CircuitBreaker.class);

        Assertions.assertNotNull(circuitBreaker);
        assertThat(circuitBreaker.fallbackMethod()).isEqualTo("getItemDetailsFallback");

        // Same parameters as the guarded method, plus the Throwable.
        Method fallback = CartResilience4j.class
                .getMethod(circuitBreaker.fallbackMethod(), Long.class, Long.class, Throwable.class);
        assertThat(fallback.getReturnType()).isEqualTo(itemDetailsMethod().getReturnType());
    }

    @Test
    @DisplayName("both guards use the same instance name, so they share the configuration")
    void bothGuardsShareTheInstanceName() throws Exception {
        assertThat(itemDetailsMethod().getAnnotation(Retry.class).name())
                .isEqualTo(itemDetailsMethod().getAnnotation(CircuitBreaker.class).name());
    }

    @Test
    @DisplayName("the guarded method is public, otherwise the Spring proxy would skip it")
    void theGuardedMethodIsPublic() throws Exception {
        assertThat(java.lang.reflect.Modifier.isPublic(itemDetailsMethod().getModifiers())).isTrue();
        assertThat(java.lang.reflect.Modifier.isFinal(itemDetailsMethod().getModifiers())).isFalse();
    }
}

