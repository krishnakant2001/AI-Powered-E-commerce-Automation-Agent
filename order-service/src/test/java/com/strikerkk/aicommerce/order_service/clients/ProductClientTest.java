package com.strikerkk.aicommerce.order_service.clients;

import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductClient")
class ProductClientTest {

    private Method itemDetailsMethod() throws Exception {
        return ProductClient.class.getMethod("getProductItemDetails", Long.class, Long.class);
    }

    @Test
    @DisplayName("is a Feign client targeting 'product-service' by its Eureka id")
    void isAFeignClientOnTheServiceId() {
        FeignClient annotation = ProductClient.class.getAnnotation(FeignClient.class);

        assertThat(annotation).isNotNull();
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
        assertThat(mapping.value()).containsExactly("/products/{productId}/variants/{variantId}/item-info");
    }

    @Test
    @DisplayName("binds both ids as path variables, in the order of the URL")
    void bindsBothIdsAsPathVariables() throws Exception {
        Annotation[][] parameters = itemDetailsMethod().getParameterAnnotations();

        assertThat(parameters).hasDimensions(2, 1);
        assertThat(parameters[0][0]).isInstanceOf(PathVariable.class);
        assertThat(parameters[1][0]).isInstanceOf(PathVariable.class);
        assertThat(itemDetailsMethod().getParameterTypes()).containsExactly(Long.class, Long.class);
    }

    @Test
    @DisplayName("brings back the whole item, price and snapshot included")
    void bringsBackTheWholeItem() throws Exception {
        assertThat(itemDetailsMethod().getReturnType()).isEqualTo(ProductItemResponse.class);
    }
}

