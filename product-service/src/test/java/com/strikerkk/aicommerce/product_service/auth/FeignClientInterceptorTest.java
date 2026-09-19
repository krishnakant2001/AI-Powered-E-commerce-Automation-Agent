package com.strikerkk.aicommerce.product_service.auth;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeignClientInterceptor")
class FeignClientInterceptorTest {

    private final FeignClientInterceptor interceptor = new FeignClientInterceptor();

    private RequestTemplate template;

    @BeforeEach
    void setUp() {
        template = new RequestTemplate();
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    @AfterEach
    void tearDown() {
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    @Test
    @DisplayName("propagates the caller id and the caller role downstream")
    void shouldPropagateBothHeaders() {
        UserContext.setUserId("admin-1");
        UserContext.setUserRole("ADMIN");

        interceptor.apply(template);

        assertThat(template.headers().get("X-user-id")).containsExactly("admin-1");
        assertThat(template.headers().get("X-user-role")).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("sends no header at all for an anonymous call")
    void shouldSendNoHeaderWhenAnonymous() {
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys("X-user-id", "X-user-role");
    }

    @Test
    @DisplayName("sends only the id when the role is unknown")
    void shouldSendOnlyTheId() {
        UserContext.setUserId("admin-1");

        interceptor.apply(template);

        assertThat(template.headers().get("X-user-id")).containsExactly("admin-1");
        assertThat(template.headers()).doesNotContainKey("X-user-role");
    }

    @Test
    @DisplayName("sends only the role when the id is unknown")
    void shouldSendOnlyTheRole() {
        UserContext.setUserRole("ADMIN");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-user-id");
        assertThat(template.headers().get("X-user-role")).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("keeps the headers already set on the template")
    void shouldKeepTheExistingHeaders() {
        template.header("X-correlation-id", "corr-1");
        UserContext.setUserId("admin-1");
        UserContext.setUserRole("ADMIN");

        interceptor.apply(template);

        assertThat(template.headers().get("X-correlation-id")).containsExactly("corr-1");
        assertThat(template.headers().get("X-user-id")).containsExactly("admin-1");
    }
}

