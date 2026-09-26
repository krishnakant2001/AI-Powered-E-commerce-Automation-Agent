package com.strikerkk.aicommerce.order_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserInterceptor")
class UserInterceptorTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ROLE_HEADER = "X-user-role";

    private final UserInterceptor interceptor = new UserInterceptor();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private boolean preHandle(MockHttpServletRequest request) throws Exception {
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    @DisplayName("copies both gateway headers into the context")
    void shouldCopyBothHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(USER_ID_HEADER, "42");
        request.addHeader(USER_ROLE_HEADER, "USER");

        assertThat(preHandle(request)).isTrue();

        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("lets the request continue even without any header")
    void shouldLetAnAnonymousRequestContinue() throws Exception {
        assertThat(preHandle(new MockHttpServletRequest())).isTrue();

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("copies the id even when the role is missing")
    void shouldCopyTheIdAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(USER_ID_HEADER, "42");

        preHandle(request);

        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("copies the role even when the id is missing")
    void shouldCopyTheRoleAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(USER_ROLE_HEADER, "ADMIN");

        preHandle(request);

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("reads the headers case insensitively, as HTTP demands")
    void shouldReadTheHeadersCaseInsensitively() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-ID", "42");
        request.addHeader("x-user-role", "USER");

        preHandle(request);

        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("takes the header as it comes - the gateway already validated the token")
    void shouldNotValidateTheHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(USER_ID_HEADER, "not-a-number");

        preHandle(request);

        assertThat(UserContext.getUserId()).isEqualTo("not-a-number");
    }

    @Test
    @DisplayName("an absent header never overwrites what is already in the context")
    void shouldNotOverwriteWithNull() throws Exception {
        UserContext.setUserId("42");
        UserContext.setUserRole("USER");

        preHandle(new MockHttpServletRequest());

        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("never writes anything to the response")
    void shouldNotTouchTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(USER_ID_HEADER, "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("is a HandlerInterceptor Spring can register")
    void isAHandlerInterceptor() {
        assertThat(HandlerInterceptor.class).isAssignableFrom(UserInterceptor.class);
        assertThat(UserInterceptor.class.getAnnotation(org.springframework.stereotype.Component.class))
                .isNotNull();
    }

    @Test
    @DisplayName("does not clear the context itself - the caller has to")
    void doesNotClearTheContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(USER_ID_HEADER, "42");

        preHandle(request);

        // No afterCompletion override exists, so the value survives the call.
        assertThat(UserContext.getUserId()).isEqualTo("42");
    }
}

