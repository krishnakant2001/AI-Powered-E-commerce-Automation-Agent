package com.strikerkk.aicommerce.cart_service.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebConfig")
class WebConfigTest {

    private WebConfig webConfig(UserInterceptor interceptor) {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "userInterceptor", interceptor);
        return config;
    }

    @SuppressWarnings("unchecked")
    private List<Object> registrationsOf(InterceptorRegistry registry) {
        return (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
    }

    @Test
    @DisplayName("is a WebMvcConfigurer, so Spring picks the registration up")
    void isAWebMvcConfigurer() {
        assertThat(WebMvcConfigurer.class).isAssignableFrom(WebConfig.class);
    }

    @Test
    @DisplayName("registers the UserInterceptor")
    void shouldRegisterTheUserInterceptor() {
        UserInterceptor interceptor = new UserInterceptor();
        InterceptorRegistry registry = new InterceptorRegistry();

        webConfig(interceptor).addInterceptors(registry);

        assertThat(registrationsOf(registry)).containsExactly(interceptor);
    }

    @Test
    @DisplayName("registers exactly one interceptor")
    void shouldRegisterExactlyOneInterceptor() {
        InterceptorRegistry registry = new InterceptorRegistry();

        webConfig(new UserInterceptor()).addInterceptors(registry);

        assertThat(registrationsOf(registry)).hasSize(1);
    }

    @Test
    @DisplayName("registers it for every path - the whole API sits behind the gateway")
    void shouldRegisterItForEveryPath() {
        InterceptorRegistry registry = new InterceptorRegistry();

        webConfig(new UserInterceptor()).addInterceptors(registry);

        // A MappedInterceptor would mean addPathPatterns / excludePathPatterns was used.
        assertThat(registrationsOf(registry).getFirst()).isNotInstanceOf(MappedInterceptor.class);
    }

    @Test
    @DisplayName("uses the Spring managed singleton, it never news up its own interceptor")
    void shouldUseTheInjectedSingleton() {
        UserInterceptor first = new UserInterceptor();
        UserInterceptor second = new UserInterceptor();

        InterceptorRegistry registryA = new InterceptorRegistry();
        InterceptorRegistry registryB = new InterceptorRegistry();

        webConfig(first).addInterceptors(registryA);
        webConfig(second).addInterceptors(registryB);

        assertThat(registrationsOf(registryA)).containsExactly(first);
        assertThat(registrationsOf(registryB)).containsExactly(second);
    }

    @Test
    @DisplayName("calling it twice registers the interceptor twice - Spring only calls it once")
    void addInterceptorsIsNotIdempotent() {
        UserInterceptor interceptor = new UserInterceptor();
        InterceptorRegistry registry = new InterceptorRegistry();
        WebConfig config = webConfig(interceptor);

        config.addInterceptors(registry);
        config.addInterceptors(registry);

        assertThat(registrationsOf(registry)).containsExactly(interceptor, interceptor);
    }
}




