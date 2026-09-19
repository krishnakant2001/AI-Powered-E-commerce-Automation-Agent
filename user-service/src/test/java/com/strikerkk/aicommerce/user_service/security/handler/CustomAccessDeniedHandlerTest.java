package com.strikerkk.aicommerce.user_service.security.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomAccessDeniedHandler")
class CustomAccessDeniedHandlerTest {

    private CustomAccessDeniedHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new CustomAccessDeniedHandler();
        request = new MockHttpServletRequest("GET", "/admin/all/user/details");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("answers 403 with a JSON body")
    void shouldReturnForbiddenJsonBody() throws Exception {
        handler.handle(request, response, new AccessDeniedException("Access is denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\": 403")
                .contains("\"error\": \"Forbidden\"")
                .contains("Access denied - you do not have permission.");
    }

    @Test
    @DisplayName("never leaks the original exception message to the caller")
    void shouldNotLeakExceptionMessage() throws Exception {
        handler.handle(request, response, new AccessDeniedException("internal detail that must not leak"));

        assertThat(response.getContentAsString()).doesNotContain("internal detail that must not leak");
    }
}

