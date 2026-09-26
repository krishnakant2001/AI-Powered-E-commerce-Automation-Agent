package com.strikerkk.aicommerce.agent_service.auth;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeignClientInterceptor")
class FeignClientInterceptorTest {

    private final FeignClientInterceptor interceptor = new FeignClientInterceptor();

    @AfterEach
    void tearDown() {
        UserContext.clear();
        UserContext.setUserRole(null);
        UserContext.setUserEmail(null);
    }

    private Collection<String> header(RequestTemplate template, String name) {
        return template.headers().get(name);
    }

    @Test
    @DisplayName("is registered as a Spring bean so OpenFeign picks it up")
    void isASpringBean() {
        assertThat(FeignClientInterceptor.class.getAnnotation(Component.class)).isNotNull();
        assertThat(interceptor).isInstanceOf(feign.RequestInterceptor.class);
    }

    @Test
    @DisplayName("forwards the caller identity to the downstream service")
    void forwardsTheCallerIdentity() {
        UserContext.setUserId("42");
        UserContext.setUserRole("USER");
        UserContext.setUserEmail("striker@example.com");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(header(template, "X-user-id")).containsExactly("42");
        assertThat(header(template, "X-user-role")).containsExactly("USER");
        assertThat(header(template, "X-user-email")).containsExactly("striker@example.com");
    }

    @Test
    @DisplayName("adds nothing when the context is empty")
    void addsNothingWhenTheContextIsEmpty() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    @DisplayName("only the headers that are actually known are added")
    void onlyKnownHeadersAreAdded() {
        UserContext.setUserId("42");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).containsOnlyKeys("X-user-id");
    }

    @Test
    @DisplayName("does not disturb headers that were already on the template")
    void doesNotDisturbExistingHeaders() {
        UserContext.setUserId("42");

        RequestTemplate template = new RequestTemplate();
        template.header("Content-Type", "application/json");

        interceptor.apply(template);

        assertThat(header(template, "Content-Type")).containsExactly("application/json");
        assertThat(header(template, "X-user-id")).containsExactly("42");
    }
}

