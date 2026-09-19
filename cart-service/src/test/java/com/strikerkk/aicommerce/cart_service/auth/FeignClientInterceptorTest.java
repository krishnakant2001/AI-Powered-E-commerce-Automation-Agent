package com.strikerkk.aicommerce.cart_service.auth;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeignClientInterceptor")
class FeignClientInterceptorTest {

    private static final String USER_ID_HEADER = "X-user-id";

    private final FeignClientInterceptor interceptor = new FeignClientInterceptor();

    private RequestTemplate template;

    @BeforeEach
    void setUp() {
        template = new RequestTemplate();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("forwards the caller id to the downstream service")
    void shouldForwardTheUserId() {
        UserContext.setUserId("42");

        interceptor.apply(template);

        assertThat(template.headers()).containsKey(USER_ID_HEADER);
        assertThat(template.headers().get(USER_ID_HEADER)).containsExactly("42");
    }

    @Test
    @DisplayName("adds no header at all for an anonymous call")
    void shouldNotAddTheHeaderWhenAnonymous() {
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(USER_ID_HEADER);
    }

    @Test
    @DisplayName("keeps the headers that were already on the template")
    void shouldPreserveTheOtherHeaders() {
        template.header("Accept", "application/json");
        UserContext.setUserId("42");

        interceptor.apply(template);

        assertThat(template.headers()).containsKeys("Accept", USER_ID_HEADER);
    }

    @Test
    @DisplayName("reflects the value of the current thread at call time")
    void shouldReadTheCurrentThreadValue() {
        UserContext.setUserId("42");
        interceptor.apply(template);

        RequestTemplate second = new RequestTemplate();
        UserContext.setUserId("99");
        interceptor.apply(second);

        assertThat(template.headers().get(USER_ID_HEADER)).containsExactly("42");
        assertThat(second.headers().get(USER_ID_HEADER)).containsExactly("99");
    }

    @Test
    @DisplayName("applying it twice with the same id does not duplicate the header")
    void isIdempotentForTheSameId() {
        UserContext.setUserId("42");

        interceptor.apply(template);
        interceptor.apply(template);

        // Feign de-duplicates identical values of the same header.
        assertThat(template.headers().get(USER_ID_HEADER)).containsExactly("42");
    }

    @Test
    @DisplayName("a second, different id is appended rather than replacing the first one")
    void appendsADifferentId() {
        UserContext.setUserId("42");
        interceptor.apply(template);

        UserContext.setUserId("99");
        interceptor.apply(template);

        assertThat(template.headers().get(USER_ID_HEADER)).containsExactly("42", "99");
    }

    @Test
    @DisplayName("a non numeric id is forwarded verbatim")
    void shouldForwardANonNumericId() {
        UserContext.setUserId("not-a-number");

        interceptor.apply(template);

        assertThat(template.headers().get(USER_ID_HEADER)).containsExactly("not-a-number");
    }
}


