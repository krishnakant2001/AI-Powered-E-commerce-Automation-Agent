package com.strikerkk.aicommerce.product_service.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebConfig")
class WebConfigTest {

    @Test
    @DisplayName("registers the UserInterceptor on the MVC pipeline")
    void shouldRegisterTheUserInterceptor() {
        WebConfig webConfig = new WebConfig();
        UserInterceptor userInterceptor = new UserInterceptor();
        ReflectionTestUtils.setField(webConfig, "userInterceptor", userInterceptor);

        InterceptorRegistry registry = new InterceptorRegistry();
        webConfig.addInterceptors(registry);

        List<Object> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        assertThat(interceptors).isNotNull();
        assertThat(interceptors).containsExactly(userInterceptor);
    }

    @Test
    @DisplayName("registers the interceptor for every route - no path pattern is excluded")
    void shouldRegisterTheInterceptorForEveryRoute() {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "userInterceptor", new UserInterceptor());

        InterceptorRegistry registry = new InterceptorRegistry();
        webConfig.addInterceptors(registry);

        List<Object> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        assertThat(interceptors).hasSize(1);
        assertThat(interceptors.getFirst()).isInstanceOf(HandlerInterceptor.class);
    }

    @Test
    @DisplayName("is a WebMvcConfigurer so Spring picks it up automatically")
    void shouldBeAWebMvcConfigurer() {
        assertThat(new WebConfig()).isInstanceOf(WebMvcConfigurer.class);
    }
}

