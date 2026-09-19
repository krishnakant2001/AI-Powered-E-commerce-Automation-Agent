package com.strikerkk.aicommerce.cart_service.integration;

import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.service.CartResilience4j;
import com.strikerkk.aicommerce.cart_service.support.TestDataFactory;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("Resilience of the product-service call")
class CartResilienceIntegrationTest {

    private static final String INSTANCE = "product-service-call";

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;

    @Autowired
    private CartResilience4j cartResilience4j;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @MockitoBean
    private ProductClient productClient;

    @BeforeEach
    void resetTheGuards() {
        // The registries are shared by the whole context: start every test from a closed breaker.
        circuitBreakerRegistry.circuitBreaker(INSTANCE).reset();
        reset(productClient);
    }

    private FeignException serviceUnavailable() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/products/1/variants/2/item-info",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);

        Response response = Response.builder()
                .status(503)
                .reason("Service Unavailable")
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        return FeignException.errorStatus("ProductClient#getProductItemDetails(Long,Long)", response);
    }

    // configuration ------------------------------------------------------------------

    @Test
    @DisplayName("the retry instance is configured with 3 attempts and a 500 ms wait")
    void theRetryIsConfigured() {
        var config = retryRegistry.retry(INSTANCE).getRetryConfig();

        assertThat(config.getMaxAttempts()).isEqualTo(3);
        assertThat(config.getIntervalBiFunction().apply(1, null)).isEqualTo(500L);
    }

    @Test
    @DisplayName("the circuit breaker opens at a 50 % failure rate over a window of 10")
    void theCircuitBreakerIsConfigured() {
        var config = circuitBreakerRegistry.circuitBreaker(INSTANCE).getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }

    @Test
    @DisplayName("the breaker starts closed")
    void theBreakerStartsClosed() {
        assertThat(circuitBreakerRegistry.circuitBreaker(INSTANCE).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // behaviour ------------------------------------------------------------------

    @Test
    @DisplayName("a healthy product-service is called exactly once")
    void aHealthyCallIsNotRetried() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenReturn(TestDataFactory.productCartResponse());

        assertThat(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).isNotNull();

        verify(productClient, times(1)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("a FeignException is NOT retried - the fallback swallows it first (documents the bug)")
    void aFeignExceptionIsNotRetried() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenThrow(serviceUnavailable());

        assertThatThrownBy(() -> cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product Service unavailable")
                .hasCauseInstanceOf(FeignException.class);

        // Would be 3 if the fallback were declared on @Retry instead of on @CircuitBreaker.
        verify(productClient, times(1)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("a transient blip is NOT absorbed - the very first failure is fatal")
    void aTransientBlipIsNotAbsorbed() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenThrow(serviceUnavailable())
                .thenReturn(TestDataFactory.productCartResponse());

        assertThatThrownBy(() -> cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product Service unavailable");

        verify(productClient, times(1)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("the circuit breaker still records the failure, so it does open eventually")
    void theBreakerStillRecordsTheFailure() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenThrow(serviceUnavailable());
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);

        long before = breaker.getMetrics().getNumberOfFailedCalls();
        assertThatThrownBy(() -> cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isGreaterThan((int) before);
    }

    @Test
    @DisplayName("an exception outside retry-exceptions is not retried, but still hits the fallback")
    void anUnlistedExceptionIsNotRetried() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenThrow(new IllegalArgumentException("bad request"));

        assertThatThrownBy(() -> cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product Service unavailable");

        verify(productClient, times(1)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("the failure reaching the caller always carries the original cause")
    void theFailureCarriesTheCause() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenThrow(new IllegalArgumentException("bad request"));

        assertThatThrownBy(() -> cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("once the breaker is open the call is short circuited without touching the client")
    void anOpenBreakerShortCircuitsTheCall() {
        circuitBreakerRegistry.circuitBreaker(INSTANCE).transitionToOpenState();

        assertThatThrownBy(() -> cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product Service unavailable");

        verify(productClient, times(0)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("the guarded bean really is a proxy - the annotations are not dead metadata")
    void theBeanIsProxied() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(cartResilience4j)).isTrue();
    }
}



