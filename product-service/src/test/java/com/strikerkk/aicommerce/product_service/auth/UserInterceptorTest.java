package com.strikerkk.aicommerce.product_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserInterceptor")
class UserInterceptorTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ROLE_HEADER = "X-user-role";

    private final UserInterceptor interceptor = new UserInterceptor();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    @Test
    @DisplayName("copies the gateway headers into the UserContext")
    void shouldCopyTheHeadersIntoTheUserContext() throws Exception {
        request.addHeader(USER_ID_HEADER, "admin-1");
        request.addHeader(USER_ROLE_HEADER, "ADMIN");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(UserContext.getUserId()).isEqualTo("admin-1");
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("registers a ROLE_ prefixed authority so @PreAuthorize can be used")
    void shouldRegisterThePrefixedAuthority() throws Exception {
        request.addHeader(USER_ID_HEADER, "admin-1");
        request.addHeader(USER_ROLE_HEADER, "ADMIN");

        interceptor.preHandle(request, response, new Object());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("admin-1");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("registers ROLE_USER for a regular customer")
    void shouldRegisterTheUserRole() throws Exception {
        request.addHeader(USER_ID_HEADER, "42");
        request.addHeader(USER_ROLE_HEADER, "USER");

        interceptor.preHandle(request, response, new Object());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("leaves the request anonymous when no header is present")
    void shouldStayAnonymousWithoutHeaders() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("does not authenticate when only the id header is present")
    void shouldNotAuthenticateWithoutTheRoleHeader() throws Exception {
        request.addHeader(USER_ID_HEADER, "admin-1");

        interceptor.preHandle(request, response, new Object());

        assertThat(UserContext.getUserId()).isEqualTo("admin-1");
        assertThat(UserContext.getUserRole()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("does not authenticate when only the role header is present")
    void shouldNotAuthenticateWithoutTheIdHeader() throws Exception {
        request.addHeader(USER_ROLE_HEADER, "ADMIN");

        interceptor.preHandle(request, response, new Object());

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("always lets the request through - authorisation is decided by @PreAuthorize")
    void shouldAlwaysReturnTrue() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        request.addHeader(USER_ID_HEADER, "42");
        request.addHeader(USER_ROLE_HEADER, "USER");
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("afterCompletion clears the caller id of the thread")
    void afterCompletionClearsTheUserId() throws Exception {
        request.addHeader(USER_ID_HEADER, "admin-1");
        request.addHeader(USER_ROLE_HEADER, "ADMIN");
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion runs even when the handler failed")
    void afterCompletionRunsAfterAFailure() throws Exception {
        request.addHeader(USER_ID_HEADER, "admin-1");
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("boom"));

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("an unknown role is still turned into an authority, and simply matches nothing")
    void shouldPrefixAnyRoleValue() throws Exception {
        request.addHeader(USER_ID_HEADER, "1");
        request.addHeader(USER_ROLE_HEADER, "GUEST");

        interceptor.preHandle(request, response, new Object());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_GUEST");
    }
}

