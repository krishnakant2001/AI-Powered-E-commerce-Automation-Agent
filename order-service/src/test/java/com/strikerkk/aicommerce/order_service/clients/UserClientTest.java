package com.strikerkk.aicommerce.order_service.clients;

import com.strikerkk.aicommerce.order_service.dto.ClientResponse.AddressResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserClient")
class UserClientTest {

    private Method addressMethod() throws Exception {
        return UserClient.class.getMethod("getAddressByAddressId", Long.class);
    }

    @Test
    @DisplayName("is a Feign client targeting 'user-service' by its Eureka id")
    void isAFeignClientOnTheServiceId() {
        FeignClient annotation = UserClient.class.getAnnotation(FeignClient.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("user-service");
        assertThat(annotation.url()).isEmpty();
    }

    @Test
    @DisplayName("is an interface - Feign generates the implementation")
    void isAnInterface() {
        assertThat(UserClient.class.isInterface()).isTrue();
    }

    @Test
    @DisplayName("declares exactly one call")
    void declaresExactlyOneCall() {
        assertThat(UserClient.class.getDeclaredMethods()).hasSize(1);
    }

    @Test
    @DisplayName("calls GET /users/address/{addressId}")
    void callsTheAddressEndpoint() throws Exception {
        GetMapping mapping = addressMethod().getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/users/address/{addressId}");
    }

    @Test
    @DisplayName("binds the address id as a path variable")
    void bindsTheAddressId() throws Exception {
        assertThat(addressMethod().getParameterAnnotations()[0][0]).isInstanceOf(PathVariable.class);
        assertThat(addressMethod().getParameterTypes()).containsExactly(Long.class);
    }

    @Test
    @DisplayName("brings back the full address to flatten onto the response")
    void bringsBackTheFullAddress() throws Exception {
        assertThat(addressMethod().getReturnType()).isEqualTo(AddressResponse.class);
    }
}

