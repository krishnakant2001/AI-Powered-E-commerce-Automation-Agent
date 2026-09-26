package com.strikerkk.aicommerce.agent_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserInterceptor")
class UserInterceptorTest {

    private final UserInterceptor interceptor = new UserInterceptor();

    @AfterEach
    void tearDown() {
        UserContext.clear();
        UserContext.setUserRole(null);
        UserContext.setUserEmail(null);
    }

    private MockHttpServletRequest requestWith(String id, String role, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) {
            request.addHeader("X-user-id", id);
        }
        if (role != null) {
            request.addHeader("X-user-role", role);
        }
        if (email != null) {
            request.addHeader("X-user-email", email);
        }
        return request;
    }

    @Test
    @DisplayName("is a Spring MVC HandlerInterceptor")
    void isAHandlerInterceptor() {
        assertThat(interceptor).isInstanceOf(HandlerInterceptor.class);
    }

    @Test
    @DisplayName("copies the three gateway headers into the UserContext")
    void copiesTheGatewayHeaders() throws Exception {
        boolean proceed = interceptor.preHandle(
                requestWith("42", "USER", "striker@example.com"),
                new MockHttpServletResponse(),
                new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isEqualTo("USER");
        assertThat(UserContext.getUserEmail()).isEqualTo("striker@example.com");
    }

    @Test
    @DisplayName("header lookup is case insensitive, as the servlet spec demands")
    void headerLookupIsCaseInsensitive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-ID", "42");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("lets an anonymous request through without touching the context")
    void letsAnAnonymousRequestThrough() throws Exception {
        boolean proceed = interceptor.preHandle(
                requestWith(null, null, null), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
        assertThat(UserContext.getUserEmail()).isNull();
    }

    @Test
    @DisplayName("a missing header never overwrites what is already there")
    void aMissingHeaderNeverOverwrites() throws Exception {
        interceptor.preHandle(requestWith("42", "USER", null), new MockHttpServletResponse(), new Object());

        assertThat(UserContext.getUserEmail()).isNull();
        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("always returns true so the handler chain keeps going")
    void alwaysReturnsTrue() throws Exception {
        assertThat(interceptor.preHandle(requestWith("42", null, null),
                new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(requestWith(null, null, null),
                new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    @DisplayName("afterCompletion clears the id so the pooled thread is clean")
    void afterCompletionClearsTheId() throws Exception {
        interceptor.preHandle(requestWith("42", "USER", "striker@example.com"),
                new MockHttpServletResponse(), new Object());

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object(), null);

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion still clears when the request blew up")
    void afterCompletionClearsOnFailure() throws Exception {
        UserContext.setUserId("42");

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object(), new IllegalStateException("boom"));

        assertThat(UserContext.getUserId()).isNull();
    }
}

