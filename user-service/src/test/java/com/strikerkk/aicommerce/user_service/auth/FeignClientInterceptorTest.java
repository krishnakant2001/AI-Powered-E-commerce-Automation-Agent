package com.strikerkk.aicommerce.user_service.auth;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeignClientInterceptor")
class FeignClientInterceptorTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ROLE_HEADER = "X-user-role";

    private FeignClientInterceptor feignClientInterceptor;
    private RequestTemplate requestTemplate;

    @BeforeEach
    void setUp() {
        feignClientInterceptor = new FeignClientInterceptor();
        requestTemplate = new RequestTemplate();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("propagates the user id and the user role to the downstream service")
    void shouldPropagateBothHeaders() {
        UserContext.setUserId("7");
        UserContext.setUserRole("ADMIN");

        feignClientInterceptor.apply(requestTemplate);

        assertThat(requestTemplate.headers()).containsKeys(USER_ID_HEADER, USER_ROLE_HEADER);
        assertThat(requestTemplate.headers().get(USER_ID_HEADER)).containsExactly("7");
        assertThat(requestTemplate.headers().get(USER_ROLE_HEADER)).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("propagates only the user id when the role is unknown")
    void shouldPropagateOnlyUserId() {
        UserContext.setUserId("7");

        feignClientInterceptor.apply(requestTemplate);

        assertThat(requestTemplate.headers()).containsKey(USER_ID_HEADER);
        assertThat(requestTemplate.headers()).doesNotContainKey(USER_ROLE_HEADER);
    }

    @Test
    @DisplayName("propagates only the role when the user id is unknown")
    void shouldPropagateOnlyUserRole() {
        UserContext.setUserRole("USER");

        feignClientInterceptor.apply(requestTemplate);

        assertThat(requestTemplate.headers()).containsKey(USER_ROLE_HEADER);
        assertThat(requestTemplate.headers()).doesNotContainKey(USER_ID_HEADER);
    }

    @Test
    @DisplayName("adds no header at all for an anonymous call")
    void shouldNotAddAnyHeaderForAnonymousCall() {
        feignClientInterceptor.apply(requestTemplate);

        assertThat(requestTemplate.headers()).isEmpty();
    }
}

