package com.strikerkk.aicommerce.cart_service.clients;

import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductClient")
class ProductClientTest {

    private Method itemDetailsMethod() throws Exception {
        return ProductClient.class.getMethod("getProductItemDetails", Long.class, Long.class);
    }

    @Test
    @DisplayName("is a Feign client")
    void isAFeignClient() {
        assertThat(ProductClient.class.getAnnotation(FeignClient.class)).isNotNull();
    }

    @Test
    @DisplayName("targets 'product-service' by its Eureka service id, never a hard coded host")
    void targetsProductServiceByServiceId() {
        FeignClient annotation = ProductClient.class.getAnnotation(FeignClient.class);

        assertThat(annotation.name()).isEqualTo("product-service");
        assertThat(annotation.url()).isEmpty();
    }

    @Test
    @DisplayName("is an interface - Feign generates the implementation")
    void isAnInterface() {
        assertThat(ProductClient.class.isInterface()).isTrue();
    }

    @Test
    @DisplayName("declares exactly one call")
    void declaresExactlyOneCall() {
        assertThat(ProductClient.class.getDeclaredMethods()).hasSize(1);
    }

    @Test
    @DisplayName("calls GET /products/{productId}/variants/{variantId}/item-info")
    void callsTheItemInfoEndpoint() throws Exception {
        GetMapping mapping = itemDetailsMethod().getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value())
                .containsExactly("/products/{productId}/variants/{variantId}/item-info");
    }

    @Test
    @DisplayName("binds both ids as path variables, in that order")
    void bindsBothPathVariables() throws Exception {
        Method method = itemDetailsMethod();

        assertThat(method.getParameterCount()).isEqualTo(2);
        assertThat(method.getParameterTypes()).containsExactly(Long.class, Long.class);
        assertThat(method.getParameters()[0].getAnnotation(org.springframework.web.bind.annotation.PathVariable.class))
                .isNotNull();
        assertThat(method.getParameters()[1].getAnnotation(org.springframework.web.bind.annotation.PathVariable.class))
                .isNotNull();
    }

    @Test
    @DisplayName("returns the ProductCartResponse payload")
    void returnsTheProductCartResponse() throws Exception {
        assertThat(itemDetailsMethod().getReturnType()).isEqualTo(ProductCartResponse.class);
    }

    @Test
    @DisplayName("declares no checked exception - a failure surfaces as a FeignException")
    void declaresNoCheckedException() throws Exception {
        assertThat(itemDetailsMethod().getExceptionTypes()).isEmpty();
    }
}

