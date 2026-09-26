package com.strikerkk.aicommerce.agent_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Custom exceptions")
class CustomExceptionsTest {

    @Nested
    @DisplayName("AgentException")
    class AgentExceptionTest {

        @Test
        @DisplayName("is unchecked")
        void isUnchecked() {
            assertThat(RuntimeException.class).isAssignableFrom(AgentException.class);
        }

        @Test
        @DisplayName("keeps the message it was given")
        void keepsTheMessage() {
            assertThat(new AgentException("LLM api error: 500"))
                    .hasMessage("LLM api error: 500")
                    .hasNoCause();
        }

        @Test
        @DisplayName("keeps the cause when one is supplied")
        void keepsTheCause() {
            Throwable cause = new IllegalStateException("socket closed");

            assertThat(new AgentException("Failed to call LLM api", cause))
                    .hasMessage("Failed to call LLM api")
                    .hasCause(cause);
        }

        @Test
        @DisplayName("is throwable")
        void isThrowable() {
            assertThatThrownBy(() -> {
                throw new AgentException("boom");
            }).isInstanceOf(AgentException.class).hasMessage("boom");
        }
    }

    @Nested
    @DisplayName("SessionNotFoundException")
    class SessionNotFoundExceptionTest {

        @Test
        @DisplayName("prefixes the session id with a readable message")
        void prefixesTheSessionId() {
            assertThat(new SessionNotFoundException("abc-123"))
                    .hasMessage("Session not found: abc-123");
        }

        @Test
        @DisplayName("is unchecked")
        void isUnchecked() {
            assertThat(RuntimeException.class).isAssignableFrom(SessionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("UnauthorizedSessionAccessException")
    class UnauthorizedSessionAccessExceptionTest {

        @Test
        @DisplayName("explains that the session belongs to somebody else")
        void explainsTheProblem() {
            assertThat(new UnauthorizedSessionAccessException("abc-123"))
                    .hasMessage("You are not authorized to access session: abc-123");
        }

        @Test
        @DisplayName("is unchecked")
        void isUnchecked() {
            assertThat(RuntimeException.class).isAssignableFrom(UnauthorizedSessionAccessException.class);
        }
    }

    @Nested
    @DisplayName("ToolCallException")
    class ToolCallExceptionTest {

        @Test
        @DisplayName("remembers which tool failed")
        void remembersTheTool() {
            ToolCallException ex = new ToolCallException("addToCart", "cart-service is down");

            assertThat(ex.getToolName()).isEqualTo("addToCart");
            assertThat(ex).hasMessage("cart-service is down").hasNoCause();
        }

        @Test
        @DisplayName("keeps the tool name and the cause together")
        void keepsToolAndCause() {
            Throwable cause = new IllegalStateException("connect timed out");

            ToolCallException ex = new ToolCallException("placeOrder", "order-service is down", cause);

            assertThat(ex.getToolName()).isEqualTo("placeOrder");
            assertThat(ex).hasMessage("order-service is down").hasCause(cause);
        }

        @Test
        @DisplayName("is unchecked")
        void isUnchecked() {
            assertThat(RuntimeException.class).isAssignableFrom(ToolCallException.class);
        }
    }

    @Nested
    @DisplayName("BadRequestException")
    class BadRequestExceptionTest {

        @Test
        @DisplayName("keeps the message verbatim")
        void keepsTheMessage() {
            assertThat(new BadRequestException("Message cannot be blank"))
                    .hasMessage("Message cannot be blank");
        }

        @Test
        @DisplayName("is unchecked")
        void isUnchecked() {
            assertThat(RuntimeException.class).isAssignableFrom(BadRequestException.class);
        }
    }
}

