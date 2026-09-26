package com.strikerkk.aicommerce.agent_service.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebConfig")
class WebConfigTest {

    private final UserInterceptor userInterceptor = new UserInterceptor();

    private WebConfig webConfig() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "userInterceptor", userInterceptor);
        return config;
    }

    @SuppressWarnings("unchecked")
    private List<Object> registrations(InterceptorRegistry registry) {
        return (List<Object>) ReflectionTestUtils.getField(registry, "registrations");
    }

    @Test
    @DisplayName("is a Spring configuration class and a WebMvcConfigurer")
    void isAConfiguration() {
        assertThat(WebConfig.class.getAnnotation(Configuration.class)).isNotNull();
        assertThat(webConfig()).isInstanceOf(WebMvcConfigurer.class);
    }

    @Test
    @DisplayName("registers exactly one interceptor")
    void registersExactlyOneInterceptor() {
        InterceptorRegistry registry = new InterceptorRegistry();

        webConfig().addInterceptors(registry);

        assertThat(registrations(registry)).hasSize(1);
    }

    @Test
    @DisplayName("the interceptor it registers is the UserInterceptor bean")
    void registersTheUserInterceptorBean() {
        InterceptorRegistry registry = new InterceptorRegistry();

        webConfig().addInterceptors(registry);

        InterceptorRegistration registration = (InterceptorRegistration) registrations(registry).get(0);
        Object interceptor = ReflectionTestUtils.getField(registration, "interceptor");

        assertThat(interceptor).isSameAs(userInterceptor);
        assertThat(interceptor).isInstanceOf(HandlerInterceptor.class);
    }

    @Test
    @DisplayName("no path pattern is configured, so it guards every endpoint")
    void guardsEveryEndpoint() {
        InterceptorRegistry registry = new InterceptorRegistry();

        webConfig().addInterceptors(registry);

        InterceptorRegistration registration = (InterceptorRegistration) registrations(registry).get(0);

        // Spring only creates these lists once addPathPatterns/excludePathPatterns is called
        assertThat((List<?>) ReflectionTestUtils.getField(registration, "includePatterns")).isNullOrEmpty();
        assertThat((List<?>) ReflectionTestUtils.getField(registration, "excludePatterns")).isNullOrEmpty();
    }
}


