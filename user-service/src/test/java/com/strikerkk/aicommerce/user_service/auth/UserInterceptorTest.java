package com.strikerkk.aicommerce.user_service.auth;

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

    private UserInterceptor userInterceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        userInterceptor = new UserInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @Test
    @DisplayName("populates the UserContext and the SecurityContext when both headers are present")
    void shouldPopulateContextsWhenBothHeadersArePresent() throws Exception {
        request.addHeader(USER_ID_HEADER, "7");
        request.addHeader(USER_ROLE_HEADER, "ADMIN");

        boolean proceed = userInterceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContext.getUserId()).isEqualTo("7");
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("7");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("prefixes the role header with ROLE_ so that @PreAuthorize(hasRole(..)) works")
    void shouldPrefixRoleWithRolePrefix() throws Exception {
        request.addHeader(USER_ID_HEADER, "7");
        request.addHeader(USER_ROLE_HEADER, "USER");

        userInterceptor.preHandle(request, response, new Object());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("stores only the user id and does not authenticate when the role header is missing")
    void shouldNotAuthenticateWhenRoleHeaderIsMissing() throws Exception {
        request.addHeader(USER_ID_HEADER, "7");

        boolean proceed = userInterceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContext.getUserId()).isEqualTo("7");
        assertThat(UserContext.getUserRole()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("stores only the role and does not authenticate when the user id header is missing")
    void shouldNotAuthenticateWhenUserIdHeaderIsMissing() throws Exception {
        request.addHeader(USER_ROLE_HEADER, "ADMIN");

        boolean proceed = userInterceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("lets an anonymous request through without touching any context")
    void shouldLetAnonymousRequestThrough() throws Exception {
        boolean proceed = userInterceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("afterCompletion clears the UserContext so the thread can be reused safely")
    void shouldClearUserContextAfterCompletion() throws Exception {
        request.addHeader(USER_ID_HEADER, "7");
        request.addHeader(USER_ROLE_HEADER, "ADMIN");
        userInterceptor.preHandle(request, response, new Object());

        userInterceptor.afterCompletion(request, response, new Object(), null);

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("afterCompletion clears the UserContext even when the request failed")
    void shouldClearUserContextWhenRequestFailed() throws Exception {
        request.addHeader(USER_ID_HEADER, "7");
        userInterceptor.preHandle(request, response, new Object());

        userInterceptor.afterCompletion(request, response, new Object(), new IllegalStateException("boom"));

        assertThat(UserContext.getUserId()).isNull();
    }
}

