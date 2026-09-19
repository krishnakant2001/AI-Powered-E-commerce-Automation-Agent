package com.strikerkk.aicommerce.user_service.security.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomAuthenticationEntryPoint")
class CustomAuthenticationEntryPointTest {

    private CustomAuthenticationEntryPoint entryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        entryPoint = new CustomAuthenticationEntryPoint();
        request = new MockHttpServletRequest("GET", "/details");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("answers 401 with a JSON body")
    void shouldReturnUnauthorizedJsonBody() throws Exception {
        entryPoint.commence(request, response,
                new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\": 401")
                .contains("\"error\": \"Unauthorized\"")
                .contains("Authentication required - Please log in to continue.");
    }

    @Test
    @DisplayName("never leaks the original exception message to the caller")
    void shouldNotLeakExceptionMessage() throws Exception {
        entryPoint.commence(request, response,
                new InsufficientAuthenticationException("internal detail that must not leak"));

        assertThat(response.getContentAsString()).doesNotContain("internal detail that must not leak");
    }
}

