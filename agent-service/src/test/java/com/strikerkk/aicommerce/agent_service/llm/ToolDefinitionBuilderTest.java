package com.strikerkk.aicommerce.agent_service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.agent_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolDefinitionBuilder")
class ToolDefinitionBuilderTest {

    private final ToolDefinitionBuilder builder = new ToolDefinitionBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> tool(String name) {
        return builder.build().stream()
                .map(entry -> (Map<String, Object>) entry)
                .filter(entry -> name.equals(entry.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool " + name + " is not advertised to the model"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaOf(String name) {
        return (Map<String, Object>) tool(name).get("input_schema");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> propertiesOf(String name) {
        return (Map<String, Object>) schemaOf(name).get("properties");
    }

    @SuppressWarnings("unchecked")
    private List<String> requiredOf(String name) {
        return (List<String>) schemaOf(name).get("required");
    }

    @Test
    @DisplayName("is a Spring component")
    void isASpringComponent() {
        assertThat(ToolDefinitionBuilder.class.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    @DisplayName("advertises exactly the twelve tools the agent can run")
    void advertisesTheTwelveTools() {
        assertThat(builder.build()).hasSize(12);

        List<String> advertised = builder.build().stream()
                .map(entry -> String.valueOf(((Map<?, ?>) entry).get("name")))
                .toList();

        assertThat(advertised).containsExactlyInAnyOrderElementsOf(TestDataFactory.allToolNames());
    }

    @Test
    @DisplayName("every tool has a name, a description and an object schema")
    void everyToolIsWellFormed() {
        for (String name : TestDataFactory.allToolNames()) {
            assertThat(tool(name).get("description")).as(name).asString().isNotBlank();
            assertThat(schemaOf(name).get("type")).as(name).isEqualTo("object");
            assertThat(schemaOf(name)).as(name).containsKeys("type", "properties", "required");
        }
    }

    @Test
    @DisplayName("every required argument is actually declared as a property")
    void everyRequiredArgumentIsDeclared() {
        for (String name : TestDataFactory.allToolNames()) {
            assertThat(propertiesOf(name).keySet())
                    .as("required args of %s", name)
                    .containsAll(requiredOf(name));
        }
    }

    @Test
    @DisplayName("every property declares a type and a description")
    void everyPropertyIsDocumented() {
        for (String name : TestDataFactory.allToolNames()) {
            propertiesOf(name).forEach((property, definition) -> {
                Map<?, ?> spec = (Map<?, ?>) definition;

                assertThat(spec.get("type")).as("%s.%s", name, property).isIn("string", "number");
                assertThat(spec.get("description")).as("%s.%s", name, property).asString().isNotBlank();
            });
        }
    }

    @Test
    @DisplayName("searchProducts takes two free-text filters and requires neither")
    void searchProducts() {
        assertThat(propertiesOf("searchProducts")).containsOnlyKeys("search", "category");
        assertThat(requiredOf("searchProducts")).isEmpty();
        assertThat(((Map<?, ?>) propertiesOf("searchProducts").get("search")).get("type")).isEqualTo("string");
    }

    @Test
    @DisplayName("getProductDetails requires the product id")
    void getProductDetails() {
        assertThat(requiredOf("getProductDetails")).containsExactly("productId");
    }

    @Test
    @DisplayName("getVariantInfo requires both ids")
    void getVariantInfo() {
        assertThat(requiredOf("getVariantInfo")).containsExactly("productId", "variantId");
    }

    @Test
    @DisplayName("addToCart requires product, variant and quantity")
    void addToCart() {
        assertThat(requiredOf("addToCart")).containsExactly("productId", "variantId", "quantity");
    }

    @Test
    @DisplayName("buyNow also needs a delivery address")
    void buyNow() {
        assertThat(requiredOf("buyNow"))
                .containsExactly("productId", "variantId", "quantity", "addressId");
    }

    @Test
    @DisplayName("placeOrder only needs the address - the cart supplies the rest")
    void placeOrder() {
        assertThat(requiredOf("placeOrder")).containsExactly("addressId");
        assertThat(propertiesOf("placeOrder")).containsOnlyKeys("addressId");
    }

    @Test
    @DisplayName("initiatePayment needs the order and the amount")
    void initiatePayment() {
        assertThat(requiredOf("initiatePayment")).containsExactly("orderId", "amount");
    }

    @Test
    @DisplayName("the no-argument tools declare an empty schema")
    void theNoArgumentToolsAreEmpty() {
        for (String name : List.of("getCart", "clearCart", "getMyOrders", "getAllAddresses")) {
            assertThat(propertiesOf(name)).as(name).isEmpty();
            assertThat(requiredOf(name)).as(name).isEmpty();
        }
    }

    @Test
    @DisplayName("the whole definition serialises to JSON - it is posted to Anthropic verbatim")
    void serialisesToJson() throws Exception {
        String json = objectMapper.writeValueAsString(builder.build());

        assertThat(json).contains("\"input_schema\"");
        assertThat(json).contains("\"name\":\"addToCart\"");
        assertThat(objectMapper.readTree(json)).hasSize(12);
    }

    @Test
    @DisplayName("is deterministic")
    void isDeterministic() throws Exception {
        assertThat(objectMapper.writeValueAsString(builder.build()))
                .isEqualTo(objectMapper.writeValueAsString(builder.build()));
    }
}


