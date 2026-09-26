package com.strikerkk.aicommerce.payment_service.clients;

import com.strikerkk.aicommerce.payment_service.common.ApiResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderClient")
class OrderClientTest {

    private Method getOrderByIdMethod() throws Exception {
        return OrderClient.class.getMethod("getOrderById", Long.class);
    }

    @Test
    @DisplayName("is a Feign client targeting 'order-service' by its Eureka id")
    void isAFeignClientOnTheServiceId() {
        FeignClient annotation = OrderClient.class.getAnnotation(FeignClient.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("order-service");
        assertThat(annotation.url()).isEmpty();
    }

    @Test
    @DisplayName("is an interface - Feign generates the implementation")
    void isAnInterface() {
        assertThat(OrderClient.class.isInterface()).isTrue();
    }

    @Test
    @DisplayName("declares exactly the one call the payment flow needs")
    void declaresExactlyOneCall() {
        assertThat(OrderClient.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsExactly("getOrderById");
    }

    @Test
    @DisplayName("reads an order with GET /orders/{orderId}")
    void readsAnOrder() throws Exception {
        GetMapping mapping = getOrderByIdMethod().getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/orders/{orderId}");
    }

    @Test
    @DisplayName("passes the order id in the path")
    void passesTheOrderIdInThePath() throws Exception {
        assertThat(getOrderByIdMethod().getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
        assertThat(getOrderByIdMethod().getParameterTypes()).containsExactly(Long.class);
    }

    @Test
    @DisplayName("never takes a user id - the header carries the caller")
    void neverTakesAUserId() throws Exception {
        assertThat(getOrderByIdMethod().getParameterCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("expects the shared ApiResponse envelope around an OrderResponse")
    void expectsTheSharedEnvelope() throws Exception {
        assertThat(getOrderByIdMethod().getReturnType()).isEqualTo(ResponseEntity.class);

        ParameterizedType responseEntity = (ParameterizedType) getOrderByIdMethod().getGenericReturnType();
        ParameterizedType envelope = (ParameterizedType) responseEntity.getActualTypeArguments()[0];

        assertThat(envelope.getRawType()).isEqualTo(ApiResponse.class);
        assertThat(envelope.getActualTypeArguments()).containsExactly(OrderResponse.class);
    }

    @Test
    @DisplayName("reads the whole ResponseEntity, so the caller can see the status code too")
    void readsTheWholeResponseEntity() throws Exception {
        assertThat(getOrderByIdMethod().getReturnType()).isEqualTo(ResponseEntity.class);
    }
}

