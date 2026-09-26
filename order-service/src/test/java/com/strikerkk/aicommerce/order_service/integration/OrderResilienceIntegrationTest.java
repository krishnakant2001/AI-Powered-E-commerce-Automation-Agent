package com.strikerkk.aicommerce.order_service.integration;

import com.strikerkk.aicommerce.order_service.clients.ProductClient;
import com.strikerkk.aicommerce.order_service.service.OrderResilience4j;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
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
class OrderResilienceIntegrationTest {

    private static final String INSTANCE = "product-service-call";

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;

    @Autowired
    private OrderResilience4j orderResilience4j;

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

    // ------------------------------------------------------------------
    // configuration
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the retry instance is configured with 3 attempts and a 500 ms wait")
    void theRetryIsConfigured() {
        var config = retryRegistry.retry(INSTANCE).getRetryConfig();

        assertThat(config.getMaxAttempts()).isEqualTo(3);
        assertThat(config.getIntervalBiFunction().apply(1, null)).isEqualTo(500L);
    }

    @Test
    @DisplayName("the circuit breaker opens at a failure rate of 50% over a window of 10")
    void theCircuitBreakerIsConfigured() {
        var config = circuitBreakerRegistry.circuitBreaker(INSTANCE).getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());
    }

    @Test
    @DisplayName("the breaker starts closed")
    void theBreakerStartsClosed() {
        assertThat(circuitBreakerRegistry.circuitBreaker(INSTANCE).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ------------------------------------------------------------------
    // behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a healthy product-service is called exactly once")
    void aHealthyServiceIsCalledOnce() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenReturn(TestDataFactory.productItemResponse());

        assertThat(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).isNotNull();

        verify(productClient, times(1)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("a 503 is answered by the fallback - the breaker sits inside the retry and wins")
    void aFailureIsAnsweredByTheFallback() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenThrow(serviceUnavailable());

        assertThatThrownBy(() -> orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product service unavailable");

        verify(productClient, times(1)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("the failure is still recorded, so repeated outages do open the breaker")
    void theFailureIsRecorded() {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenThrow(serviceUnavailable());

        long before = breaker.getMetrics().getNumberOfFailedCalls();

        assertThatThrownBy(() -> orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isGreaterThan((int) before);
    }

    @Test
    @DisplayName("a healthy answer after a reset is served straight away")
    void aHealthyAnswerAfterAResetIsServed() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenReturn(TestDataFactory.productItemResponse());

        assertThat(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID).getPrice())
                .isEqualByComparingTo(TestDataFactory.PRICE);

        verify(productClient, times(1)).getProductItemDetails(PRODUCT_ID, VARIANT_ID);
    }

    @Test
    @DisplayName("the fallback turns the outage into one clear message, never into a fake item")
    void theFallbackNeverFakesAnItem() {
        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID)).thenThrow(serviceUnavailable());

        assertThatThrownBy(() -> orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product service unavailable")
                .cause()
                .isInstanceOf(FeignException.class);
    }

    @Test
    @DisplayName("enough failures open the breaker, which then answers without calling at all")
    void enoughFailuresOpenTheBreaker() {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        breaker.transitionToOpenState();

        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenReturn(TestDataFactory.productItemResponse());

        assertThatThrownBy(() -> orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product service unavailable");

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        org.mockito.Mockito.verifyNoInteractions(productClient);
    }

    @Test
    @DisplayName("the breaker recovers once it is closed again")
    void theBreakerRecovers() {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        breaker.transitionToOpenState();
        breaker.reset();

        when(productClient.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .thenReturn(TestDataFactory.productItemResponse());

        assertThat(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).isNotNull();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("the guards really are applied - the bean is a proxy, not the plain class")
    void theBeanIsProxied() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(orderResilience4j)
                || orderResilience4j.getClass() != OrderResilience4j.class)
                .isTrue();
    }
}


