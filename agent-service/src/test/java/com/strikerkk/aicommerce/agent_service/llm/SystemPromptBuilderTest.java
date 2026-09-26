package com.strikerkk.aicommerce.agent_service.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SystemPromptBuilder")
class SystemPromptBuilderTest {

    private final SystemPromptBuilder builder = new SystemPromptBuilder();

    @Test
    @DisplayName("is a Spring component")
    void isASpringComponent() {
        assertThat(SystemPromptBuilder.class.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    @DisplayName("produces a non-empty prompt")
    void producesANonEmptyPrompt() {
        assertThat(builder.build()).isNotBlank();
    }

    @Test
    @DisplayName("tells the model what it is")
    void tellsTheModelWhatItIs() {
        assertThat(builder.build()).contains("intelligent shopping assistant");
    }

    @Test
    @DisplayName("keeps the safety rules the whole flow depends on")
    void keepsTheSafetyRules() {
        String prompt = builder.build();

        assertThat(prompt)
                .contains("Always search for products before adding to cart")
                .contains("Always check variant availability")
                .contains("ask for clarification")
                .contains("Always confirm the order details with the user before calling placeOrder or buyNow")
                .contains("Never make up product details");
    }

    @Test
    @DisplayName("is deterministic - two calls give the identical prompt")
    void isDeterministic() {
        assertThat(builder.build()).isEqualTo(builder.build());
    }

    @Test
    @DisplayName("is a multi-line text block, not one giant line")
    void isAMultiLineTextBlock() {
        assertThat(builder.build().lines().count()).isGreaterThan(5);
    }
}

