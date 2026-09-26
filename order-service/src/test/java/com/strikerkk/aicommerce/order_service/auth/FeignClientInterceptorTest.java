package com.strikerkk.aicommerce.order_service.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeignClientInterceptor")
class FeignClientInterceptorTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ROLE_HEADER = "X-user-role";

    private final FeignClientInterceptor interceptor = new FeignClientInterceptor();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private RequestTemplate apply() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        return template;
    }

    @Test
    @DisplayName("forwards the caller id to cart-, user- and product-service")
    void shouldForwardTheCallerId() {
        UserContext.setUserId("42");

        assertThat(apply().headers().get(USER_ID_HEADER)).containsExactly("42");
    }

    @Test
    @DisplayName("forwards the caller role as well")
    void shouldForwardTheCallerRole() {
        UserContext.setUserRole("USER");

        assertThat(apply().headers().get(USER_ROLE_HEADER)).containsExactly("USER");
    }

    @Test
    @DisplayName("forwards both headers together")
    void shouldForwardBothHeaders() {
        UserContext.setUserId("42");
        UserContext.setUserRole("ADMIN");

        RequestTemplate template = apply();

        assertThat(template.headers().get(USER_ID_HEADER)).containsExactly("42");
        assertThat(template.headers().get(USER_ROLE_HEADER)).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("adds nothing at all for an anonymous call")
    void shouldAddNothingForAnAnonymousCall() {
        assertThat(apply().headers()).isEmpty();
    }

    @Test
    @DisplayName("omits the role when only the id is known")
    void shouldOmitTheMissingRole() {
        UserContext.setUserId("42");

        RequestTemplate template = apply();

        assertThat(template.headers()).containsOnlyKeys(USER_ID_HEADER);
    }

    @Test
    @DisplayName("omits the id when only the role is known")
    void shouldOmitTheMissingId() {
        UserContext.setUserRole("USER");

        RequestTemplate template = apply();

        assertThat(template.headers()).containsOnlyKeys(USER_ROLE_HEADER);
    }

    @Test
    @DisplayName("never touches the target or the body of the call")
    void shouldNotTouchTheRest() {
        UserContext.setUserId("42");

        RequestTemplate template = new RequestTemplate().method(feign.Request.HttpMethod.GET).uri("/cart/items");
        interceptor.apply(template);

        assertThat(template.url()).isEqualTo("/cart/items");
        assertThat(template.method()).isEqualTo("GET");
        assertThat(template.body()).isNull();
    }

    @Test
    @DisplayName("is a Feign RequestInterceptor bean, so every client gets the headers")
    void isARegisteredFeignInterceptor() {
        assertThat(RequestInterceptor.class).isAssignableFrom(FeignClientInterceptor.class);
        assertThat(FeignClientInterceptor.class.getAnnotation(org.springframework.stereotype.Component.class))
                .isNotNull();
    }

    @Test
    @DisplayName("reads the context at call time, not at construction time")
    void shouldReadTheContextAtCallTime() {
        UserContext.setUserId("42");
        assertThat(apply().headers().get(USER_ID_HEADER)).containsExactly("42");

        UserContext.setUserId("99");
        assertThat(apply().headers().get(USER_ID_HEADER)).containsExactly("99");
    }
}

