package com.strikerkk.aicommerce.order_service.clients;

import com.strikerkk.aicommerce.order_service.dto.ClientResponse.CartItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CartClient")
class CartClientTest {

    private Method itemsMethod() throws Exception {
        return CartClient.class.getMethod("getCartItems");
    }

    private Method clearMethod() throws Exception {
        return CartClient.class.getMethod("clearCart");
    }

    @Test
    @DisplayName("is a Feign client targeting 'cart-service' by its Eureka id")
    void isAFeignClientOnTheServiceId() {
        FeignClient annotation = CartClient.class.getAnnotation(FeignClient.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("cart-service");
        assertThat(annotation.url()).isEmpty();
    }

    @Test
    @DisplayName("is an interface - Feign generates the implementation")
    void isAnInterface() {
        assertThat(CartClient.class.isInterface()).isTrue();
    }

    @Test
    @DisplayName("declares exactly the two calls the checkout needs")
    void declaresExactlyTwoCalls() {
        assertThat(CartClient.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("getCartItems", "clearCart");
    }

    @Test
    @DisplayName("reads the cart with GET /cart/items")
    void readsTheCart() throws Exception {
        GetMapping mapping = itemsMethod().getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/cart/items");
    }

    @Test
    @DisplayName("empties the cart with DELETE /cart/clear")
    void emptiesTheCart() throws Exception {
        DeleteMapping mapping = clearMethod().getAnnotation(DeleteMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/cart/clear");
    }

    @Test
    @DisplayName("reads a list of cart items, everything the snapshot needs")
    void readsAListOfCartItems() throws Exception {
        assertThat(itemsMethod().getReturnType()).isEqualTo(java.util.List.class);

        ParameterizedType generic = (ParameterizedType) itemsMethod().getGenericReturnType();
        assertThat(generic.getActualTypeArguments()).containsExactly(CartItemResponse.class);
    }

    @Test
    @DisplayName("neither call takes a user id - the header carries the caller")
    void neitherCallTakesAUserId() throws Exception {
        assertThat(itemsMethod().getParameterCount()).isZero();
        assertThat(clearMethod().getParameterCount()).isZero();
    }

    @Test
    @DisplayName("clearing the cart returns nothing")
    void clearingReturnsNothing() throws Exception {
        assertThat(clearMethod().getReturnType()).isEqualTo(void.class);
    }
}

