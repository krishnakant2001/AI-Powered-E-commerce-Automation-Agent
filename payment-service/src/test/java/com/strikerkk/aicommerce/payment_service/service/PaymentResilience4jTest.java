package com.strikerkk.aicommerce.payment_service.service;

import com.strikerkk.aicommerce.payment_service.clients.OrderClient;
import com.strikerkk.aicommerce.payment_service.common.ApiResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import com.strikerkk.aicommerce.payment_service.support.TestDataFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentResilience4j")
class PaymentResilience4jTest {

    private static final Long ORDER_ID = TestDataFactory.ORDER_ID;
    private static final String INSTANCE = "order-service-call";

    @Mock
    private OrderClient orderClient;

    @InjectMocks
    private PaymentResilience4j paymentResilience4j;

    private Method guardedMethod() throws Exception {
        return PaymentResilience4j.class.getMethod("getOrderById", Long.class);
    }

    private ResponseEntity<ApiResponse<OrderResponse>> wrap(OrderResponse order) {
        return ResponseEntity.ok(ApiResponse.success("Order found", order));
    }

    // ==================================================================
    // delegation
    // ==================================================================

    @Nested
    @DisplayName("delegation")
    class Delegation {

        @Test
        @DisplayName("unwraps the envelope and answers with the order itself")
        void unwrapsTheEnvelope() {
            OrderResponse expected = TestDataFactory.orderResponse();
            when(orderClient.getOrderById(ORDER_ID)).thenReturn(wrap(expected));

            assertThat(paymentResilience4j.getOrderById(ORDER_ID)).isSameAs(expected);
            verify(orderClient).getOrderById(ORDER_ID);
        }

        @Test
        @DisplayName("passes the order id through untouched")
        void passesTheOrderIdThrough() {
            when(orderClient.getOrderById(777L)).thenReturn(wrap(TestDataFactory.orderResponse()));

            paymentResilience4j.getOrderById(777L);

            verify(orderClient).getOrderById(777L);
        }

        @Test
        @DisplayName("adds no logic of its own - an empty envelope is answered with null")
        void addsNoLogic() {
            when(orderClient.getOrderById(ORDER_ID)).thenReturn(wrap(null));

            assertThat(paymentResilience4j.getOrderById(ORDER_ID)).isNull();
        }

        @Test
        @DisplayName("lets a client failure through, the guards deal with it")
        void letsAClientFailureThrough() {
            when(orderClient.getOrderById(ORDER_ID)).thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> paymentResilience4j.getOrderById(ORDER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("connection refused");
        }

        @Test
        @DisplayName("blows up on a bodyless answer - order-service must always send the envelope")
        void blowsUpOnABodylessAnswer() {
            when(orderClient.getOrderById(ORDER_ID)).thenReturn(ResponseEntity.noContent().build());

            assertThatThrownBy(() -> paymentResilience4j.getOrderById(ORDER_ID))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================================================================
    // the fallback
    // ==================================================================

    @Nested
    @DisplayName("the fallback")
    class Fallback {

        @Test
        @DisplayName("rethrows a RuntimeException that names the culprit")
        void rethrowsNamingTheCulprit() {
            Throwable cause = new IllegalStateException("connection refused");

            assertThatThrownBy(() -> paymentResilience4j.getOrderFallback(ORDER_ID, cause))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Order service unavailable")
                    .hasCause(cause);
        }

        @Test
        @DisplayName("never invents an order - a payment must never be started on a guess")
        void neverInventsAnOrder() throws Exception {
            assertThat(PaymentResilience4j.class
                    .getMethod("getOrderFallback", Long.class, Throwable.class)
                    .getReturnType())
                    .isEqualTo(OrderResponse.class);

            assertThatThrownBy(() -> paymentResilience4j.getOrderFallback(ORDER_ID, new RuntimeException("x")))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("tolerates a cause without a message")
        void toleratesACauseWithoutAMessage() {
            assertThatThrownBy(() -> paymentResilience4j.getOrderFallback(ORDER_ID, new RuntimeException()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Order service unavailable");
        }

        @Test
        @DisplayName("never calls order-service again")
        void neverCallsTheClientAgain() {
            assertThatThrownBy(() -> paymentResilience4j.getOrderFallback(ORDER_ID, new RuntimeException("x")))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(orderClient);
        }
    }

    // ==================================================================
    // the guards
    // ==================================================================

    @Nested
    @DisplayName("the guards")
    class Guards {

        @Test
        @DisplayName("is guarded by the 'order-service-call' retry instance")
        void isGuardedByTheRetryInstance() throws Exception {
            Retry retry = guardedMethod().getAnnotation(Retry.class);

            assertThat(retry).isNotNull();
            assertThat(retry.name()).isEqualTo(INSTANCE);
        }

        @Test
        @DisplayName("is guarded by the 'order-service-call' circuit breaker instance")
        void isGuardedByTheCircuitBreakerInstance() throws Exception {
            CircuitBreaker circuitBreaker = guardedMethod().getAnnotation(CircuitBreaker.class);

            assertThat(circuitBreaker).isNotNull();
            assertThat(circuitBreaker.name()).isEqualTo(INSTANCE);
        }

        @Test
        @DisplayName("both guards share the instance name, so they share the configuration")
        void bothGuardsShareTheInstanceName() throws Exception {
            assertThat(guardedMethod().getAnnotation(Retry.class).name())
                    .isEqualTo(guardedMethod().getAnnotation(CircuitBreaker.class).name());
        }

        @Test
        @DisplayName("the guarded method is public and non final, otherwise the proxy would skip it")
        void theGuardedMethodIsProxyable() throws Exception {
            assertThat(Modifier.isPublic(guardedMethod().getModifiers())).isTrue();
            assertThat(Modifier.isFinal(guardedMethod().getModifiers())).isFalse();
        }

        @Test
        @DisplayName("the circuit breaker points at a fallback that does not exist (documents the bug)")
        void theFallbackNameDoesNotMatch() throws Exception {
            CircuitBreaker circuitBreaker = guardedMethod().getAnnotation(CircuitBreaker.class);

            assertThat(circuitBreaker.fallbackMethod()).isEqualTo("getOrderByIdFallback");

            // The method that was actually written is called getOrderFallback, so once the breaker
            // opens Resilience4j cannot find the fallback and surfaces its own resolution failure
            // instead of the intended "Order service unavailable".
            assertThat(Arrays.stream(PaymentResilience4j.class.getMethods()).map(Method::getName))
                    .doesNotContain("getOrderByIdFallback")
                    .contains("getOrderFallback");
        }

        @Test
        @DisplayName("the fallback that exists has the signature Resilience4j expects")
        void theExistingFallbackHasTheRightSignature() throws Exception {
            Method fallback = PaymentResilience4j.class.getMethod("getOrderFallback", Long.class, Throwable.class);

            // Same parameters as the guarded method, plus the Throwable.
            assertThat(fallback.getReturnType()).isEqualTo(guardedMethod().getReturnType());
            assertThat(Modifier.isPublic(fallback.getModifiers())).isTrue();
        }
    }

    // ==================================================================
    // wiring
    // ==================================================================

    @Test
    @DisplayName("is a Spring service, so the guards are woven in by the proxy")
    void isASpringService() {
        assertThat(PaymentResilience4j.class.getAnnotation(org.springframework.stereotype.Service.class))
                .isNotNull();
    }

    @Test
    @DisplayName("wraps exactly one downstream call")
    void wrapsExactlyOneCall() {
        assertThat(PaymentResilience4j.class.getDeclaredMethods())
                .filteredOn(m -> !m.isSynthetic())
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("getOrderById", "getOrderFallback");
    }
}

