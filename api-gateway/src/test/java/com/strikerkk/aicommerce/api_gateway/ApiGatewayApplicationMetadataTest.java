package com.strikerkk.aicommerce.api_gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiGatewayApplication metadata")
class ApiGatewayApplicationMetadataTest {

    @Test
    @DisplayName("is annotated with @SpringBootApplication")
    void shouldBeAnnotatedWithSpringBootApplication() {
        assertThat(ApiGatewayApplication.class.getAnnotation(SpringBootApplication.class))
                .as("component scanning of the filters/ and service/ packages depends on it")
                .isNotNull();
    }

    @Test
    @DisplayName("lives in the base package that is component-scanned")
    void shouldLiveInTheScannedBasePackage() {
        assertThat(ApiGatewayApplication.class.getPackageName())
                .isEqualTo("com.strikerkk.aicommerce.api_gateway");
    }

    @Test
    @DisplayName("exposes a public static void main(String[])")
    void shouldExposeAStandardMainMethod() throws NoSuchMethodException {
        Method main = ApiGatewayApplication.class.getMethod("main", String[].class);

        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
        assertThat(main.getExceptionTypes()).isEmpty();
    }

    @Test
    @DisplayName("is a public, instantiable class as Spring Boot requires")
    void shouldBePublicAndInstantiable() throws Exception {
        assertThat(Modifier.isPublic(ApiGatewayApplication.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(ApiGatewayApplication.class.getModifiers())).isFalse();
        assertThat(ApiGatewayApplication.class.getDeclaredConstructor().newInstance()).isNotNull();
    }
}

