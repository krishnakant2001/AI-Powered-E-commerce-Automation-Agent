package com.strikerkk.aicommerce.cart_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserInterceptor")
class UserInterceptorTest {

    private static final String USER_ID_HEADER = "X-user-id";

    private final UserInterceptor interceptor = new UserInterceptor();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("copies the gateway header into the UserContext")
    void shouldCopyTheHeaderIntoTheUserContext() throws Exception {
        request.addHeader(USER_ID_HEADER, "42");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("the header lookup is case insensitive, as the servlet spec mandates")
    void shouldReadTheHeaderCaseInsensitively() throws Exception {
        request.addHeader("X-USER-ID", "42");

        interceptor.preHandle(request, response, new Object());

        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("leaves the context empty when the header is missing")
    void shouldStayAnonymousWithoutTheHeader() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("a non numeric id is copied verbatim - it is the service layer that parses it")
    void shouldNotValidateTheHeader() throws Exception {
        request.addHeader(USER_ID_HEADER, "not-a-number");

        interceptor.preHandle(request, response, new Object());

        assertThat(UserContext.getUserId()).isEqualTo("not-a-number");
    }

    @Test
    @DisplayName("always lets the request through - this interceptor never rejects")
    void shouldAlwaysReturnTrue() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        request.addHeader(USER_ID_HEADER, "42");
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("afterCompletion clears the caller id of the thread")
    void afterCompletionClearsTheUserId() throws Exception {
        request.addHeader(USER_ID_HEADER, "42");
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion runs even when the handler failed")
    void afterCompletionRunsAfterAFailure() throws Exception {
        request.addHeader(USER_ID_HEADER, "42");
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion is safe on an already empty context")
    void afterCompletionIsSafeWithoutAPreHandle() throws Exception {
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("preHandle does not reset a stale id when the header is absent (documents the leak)")
    void preHandleDoesNotResetAStaleId() throws Exception {
        UserContext.setUserId("previous-caller");

        interceptor.preHandle(request, response, new Object());

        assertThat(UserContext.getUserId()).isEqualTo("previous-caller");
    }
}

