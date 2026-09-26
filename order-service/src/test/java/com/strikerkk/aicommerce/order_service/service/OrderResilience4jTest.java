package com.strikerkk.aicommerce.order_service.service;

import com.strikerkk.aicommerce.order_service.clients.ProductClient;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderResilience4j")
class OrderResilience4jTest {

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;

    private static final String INSTANCE = "product-service-call";

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderResilience4j orderResilience4j;

    private Method itemDetailsMethod() throws Exception {
        return OrderResilience4j.class.getMethod("getItemDetails", Long.class, Long.class);
    }

    // delegation ------------------------------------------------------------------

    @Test
    @DisplayName("delegates straight to the Feign client")
    void delegatesToTheFeignClient() {
        ProductItemResponse expected = TestDataFactory.productItemResponse();
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenReturn(expected);

        assertThat(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).isSameAs(expected);
        verify(productClient).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("passes the product id and the variant id through in that order")
    void passesTheIdsInOrder() {
        when(productClient.getProductItemDetails(7L, 8L)).thenReturn(TestDataFactory.productItemResponse());

        orderResilience4j.getItemDetails(7L, 8L);

        verify(productClient).getProductItemDetails(7L, 8L);
    }

    @Test
    @DisplayName("adds no logic of its own - a null answer is returned as is")
    void addsNoLogic() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenReturn(null);

        assertThat(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).isNull();
    }

    @Test
    @DisplayName("lets a client failure through, the guards deal with it")
    void letsAClientFailureThrough() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("connection refused");
    }

    // the fallback ------------------------------------------------------------------

    @Test
    @DisplayName("the fallback rethrows a RuntimeException that names the culprit")
    void theFallbackRethrows() {
        Throwable cause = new IllegalStateException("connection refused");

        assertThatThrownBy(() -> orderResilience4j.getItemDetailsFallback(PRODUCT_ID, VARIANT_ID, cause))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product service unavailable")
                .hasCause(cause);
    }

    @Test
    @DisplayName("the fallback never invents a placeholder item - an order must not be priced blindly")
    void theFallbackNeverReturnsAPlaceholder() throws Exception {
        assertThat(OrderResilience4j.class
                .getMethod("getItemDetailsFallback", Long.class, Long.class, Throwable.class)
                .getReturnType())
                .isEqualTo(ProductItemResponse.class);

        assertThatThrownBy(() -> orderResilience4j.getItemDetailsFallback(1L, 2L, new RuntimeException("x")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("the fallback tolerates a cause without a message")
    void theFallbackToleratesACauseWithoutAMessage() {
        assertThatThrownBy(() -> orderResilience4j.getItemDetailsFallback(1L, 2L, new RuntimeException()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product service unavailable");
    }

    @Test
    @DisplayName("the fallback never calls product-service again")
    void theFallbackNeverCallsTheClient() {
        assertThatThrownBy(() -> orderResilience4j.getItemDetailsFallback(1L, 2L, new RuntimeException("x")))
                .isInstanceOf(RuntimeException.class);

        org.mockito.Mockito.verifyNoInteractions(productClient);
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

        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.fallbackMethod()).isEqualTo("getItemDetailsFallback");

        // Same parameters as the guarded method, plus the Throwable.
        Method fallback = OrderResilience4j.class
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
    @DisplayName("the guarded method is public and non final, otherwise the Spring proxy would skip it")
    void theGuardedMethodIsProxyable() throws Exception {
        assertThat(Modifier.isPublic(itemDetailsMethod().getModifiers())).isTrue();
        assertThat(Modifier.isFinal(itemDetailsMethod().getModifiers())).isFalse();
    }
}

