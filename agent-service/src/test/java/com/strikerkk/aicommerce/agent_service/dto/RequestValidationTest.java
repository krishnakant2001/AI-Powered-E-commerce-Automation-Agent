package com.strikerkk.aicommerce.agent_service.dto;

import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Request DTO validation")
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    private ChatRequest chatRequest(UUID sessionId, String message) {
        ChatRequest request = new ChatRequest();
        request.setSessionId(sessionId);
        request.setMessage(message);
        return request;
    }

    @Nested
    @DisplayName("ChatRequest")
    class ChatRequestTest {

        @Test
        @DisplayName("a normal message passes")
        void aNormalMessagePasses() {
            assertThat(validator.validate(chatRequest(UUID.randomUUID(), "add the black one to my cart")))
                    .isEmpty();
        }

        @Test
        @DisplayName("the sessionId is optional - the very first message has none")
        void theSessionIdIsOptional() {
            assertThat(validator.validate(chatRequest(null, "hi"))).isEmpty();
        }

        @Test
        @DisplayName("a null message is rejected")
        void aNullMessageIsRejected() {
            Set<ConstraintViolation<ChatRequest>> violations = validator.validate(chatRequest(null, null));

            assertThat(violations).singleElement()
                    .satisfies(violation -> {
                        assertThat(violation.getPropertyPath()).hasToString("message");
                        assertThat(violation.getMessage()).isEqualTo("Message cannot be blank");
                    });
        }

        @Test
        @DisplayName("an empty message is rejected")
        void anEmptyMessageIsRejected() {
            assertThat(validator.validate(chatRequest(null, "")))
                    .extracting(ConstraintViolation::getMessage)
                    .containsExactly("Message cannot be blank");
        }

        @Test
        @DisplayName("a whitespace-only message is rejected")
        void aWhitespaceOnlyMessageIsRejected() {
            assertThat(validator.validate(chatRequest(null, "   \t\n")))
                    .extracting(ConstraintViolation::getMessage)
                    .containsExactly("Message cannot be blank");
        }

        @Test
        @DisplayName("exactly 1000 characters is still allowed")
        void exactlyOneThousandCharactersIsAllowed() {
            assertThat(validator.validate(chatRequest(null, "a".repeat(1000)))).isEmpty();
        }

        @Test
        @DisplayName("1001 characters is rejected")
        void tooLongAMessageIsRejected() {
            assertThat(validator.validate(chatRequest(null, "a".repeat(1001))))
                    .extracting(ConstraintViolation::getMessage)
                    .containsExactly("Message cannot exceed 1000 characters");
        }

        @Test
        @DisplayName("equals/hashCode/toString come from @Data")
        void dataContract() {
            UUID sessionId = UUID.randomUUID();

            assertThat(chatRequest(sessionId, "hi")).isEqualTo(chatRequest(sessionId, "hi"));
            assertThat(chatRequest(sessionId, "hi")).hasSameHashCodeAs(chatRequest(sessionId, "hi"));
            assertThat(chatRequest(sessionId, "hi")).isNotEqualTo(chatRequest(sessionId, "bye"));
            assertThat(chatRequest(sessionId, "hi").toString()).contains("ChatRequest");
        }
    }

    @Nested
    @DisplayName("StartSessionRequest")
    class StartSessionRequestTest {

        @Test
        @DisplayName("is entirely optional - an empty body is valid")
        void anEmptyBodyIsValid() {
            assertThat(validator.validate(new StartSessionRequest())).isEmpty();
            assertThat(new StartSessionRequest().getInitialIntent()).isNull();
        }

        @Test
        @DisplayName("carries the initial intent when one is supplied")
        void carriesTheInitialIntent() {
            StartSessionRequest request = new StartSessionRequest();
            request.setInitialIntent("I want to buy a speaker");

            assertThat(validator.validate(request)).isEmpty();
            assertThat(request.getInitialIntent()).isEqualTo("I want to buy a speaker");
        }

        @Test
        @DisplayName("equals/hashCode come from @Data")
        void dataContract() {
            StartSessionRequest first = new StartSessionRequest();
            StartSessionRequest second = new StartSessionRequest();
            first.setInitialIntent("x");
            second.setInitialIntent("x");

            assertThat(first).isEqualTo(second);
            assertThat(first).hasSameHashCodeAs(second);
            assertThat(first.toString()).contains("StartSessionRequest");
        }
    }
}

