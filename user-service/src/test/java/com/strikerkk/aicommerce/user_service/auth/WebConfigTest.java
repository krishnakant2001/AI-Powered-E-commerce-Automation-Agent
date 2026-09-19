package com.strikerkk.aicommerce.user_service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebConfig")
class WebConfigTest {

    private WebConfig webConfig;
    private UserInterceptor userInterceptor;

    @BeforeEach
    void setUp() {
        webConfig = new WebConfig();
        userInterceptor = new UserInterceptor();
        ReflectionTestUtils.setField(webConfig, "userInterceptor", userInterceptor);
    }

    @Test
    @DisplayName("is a WebMvcConfigurer so that Spring MVC picks it up")
    void shouldBeAWebMvcConfigurer() {
        assertThat(webConfig).isInstanceOf(WebMvcConfigurer.class);
    }

    @Test
    @DisplayName("registers the UserInterceptor for every request")
    void shouldRegisterUserInterceptor() {
        InterceptorRegistry registry = new InterceptorRegistry();

        webConfig.addInterceptors(registry);

        @SuppressWarnings("unchecked")
        List<Object> interceptors = (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");

        assertThat(interceptors).isNotNull();
        assertThat(interceptors).contains(userInterceptor);
    }
}

