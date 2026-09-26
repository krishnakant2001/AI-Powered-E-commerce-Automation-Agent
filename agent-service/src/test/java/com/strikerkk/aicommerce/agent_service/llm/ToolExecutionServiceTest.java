package com.strikerkk.aicommerce.agent_service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolExecutionService")
class ToolExecutionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ToolExecutor toolExecutor;

    @InjectMocks
    private ToolExecutionService toolExecutionService;

    private JsonNode input(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private JsonNode empty() {
        return objectMapper.createObjectNode();
    }

    @Nested
    @DisplayName("Product tools")
    class ProductTools {

        @Test
        @DisplayName("searchProducts passes the search term and the category through")
        void searchProducts() {
            when(toolExecutor.searchProducts("speaker", "electronics")).thenReturn("[]");

            assertThat(toolExecutionService.executeTool("searchProducts",
                    input("{\"search\":\"speaker\",\"category\":\"electronics\"}"))).isEqualTo("[]");

            verify(toolExecutor).searchProducts("speaker", "electronics");
        }

        @Test
        @DisplayName("a missing filter arrives as null, not as the string \"null\"")
        void aMissingFilterArrivesAsNull() {
            when(toolExecutor.searchProducts("speaker", null)).thenReturn("[]");

            toolExecutionService.executeTool("searchProducts", input("{\"search\":\"speaker\"}"));

            verify(toolExecutor).searchProducts("speaker", null);
        }

        @Test
        @DisplayName("getProductDetails converts the id to a long")
        void getProductDetails() {
            when(toolExecutor.getProductDetails(7L)).thenReturn("{\"id\":7}");

            assertThat(toolExecutionService.executeTool("getProductDetails", input("{\"productId\":7}")))
                    .isEqualTo("{\"id\":7}");

            verify(toolExecutor).getProductDetails(7L);
        }

        @Test
        @DisplayName("a productId sent as a string is still understood")
        void aStringProductIdIsUnderstood() {
            when(toolExecutor.getProductDetails(7L)).thenReturn("{}");

            toolExecutionService.executeTool("getProductDetails", input("{\"productId\":\"7\"}"));

            verify(toolExecutor).getProductDetails(7L);
        }

        @Test
        @DisplayName("getVariantInfo and getProductItemDetails are two names for the same call")
        void getVariantInfoHasAnAlias() {
            when(toolExecutor.getProductItemDetails(7L, 9L)).thenReturn("{\"inStock\":true}");

            String byPrimaryName = toolExecutionService.executeTool(
                    "getVariantInfo", input("{\"productId\":7,\"variantId\":9}"));
            String byAlias = toolExecutionService.executeTool(
                    "getProductItemDetails", input("{\"productId\":7,\"variantId\":9}"));

            assertThat(byPrimaryName).isEqualTo(byAlias).isEqualTo("{\"inStock\":true}");
            verify(toolExecutor, org.mockito.Mockito.times(2)).getProductItemDetails(7L, 9L);
        }
    }

    @Nested
    @DisplayName("Cart tools")
    class CartTools {

        @Test
        @DisplayName("getCart takes no arguments")
        void getCart() {
            when(toolExecutor.getCart()).thenReturn("{\"items\":[]}");

            assertThat(toolExecutionService.executeTool("getCart", empty())).isEqualTo("{\"items\":[]}");

            verify(toolExecutor).getCart();
        }

        @Test
        @DisplayName("addToCart is rebuilt into the body cart-service expects")
        void addToCartBuildsTheBody() {
            when(toolExecutor.addToCart(anyString())).thenReturn("{\"cartItemId\":9}");

            toolExecutionService.executeTool("addToCart",
                    input("{\"productId\":7,\"variantId\":9,\"quantity\":3}"));

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(toolExecutor).addToCart(body.capture());

            assertThat(body.getValue()).isEqualTo("{\"productId\":7,\"variantId\":9,\"quantity\":3}");
        }

        @Test
        @DisplayName("a missing quantity defaults to one")
        void aMissingQuantityDefaultsToOne() {
            when(toolExecutor.addToCart(anyString())).thenReturn("{}");

            toolExecutionService.executeTool("addToCart", input("{\"productId\":7,\"variantId\":9}"));

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(toolExecutor).addToCart(body.capture());

            assertThat(body.getValue()).contains("\"quantity\":1");
        }

        @Test
        @DisplayName("the rebuilt cart body is valid JSON")
        void theRebuiltBodyIsValidJson() throws Exception {
            when(toolExecutor.addToCart(anyString())).thenReturn("{}");

            toolExecutionService.executeTool("addToCart",
                    input("{\"productId\":7,\"variantId\":9,\"quantity\":2}"));

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(toolExecutor).addToCart(body.capture());

            JsonNode parsed = objectMapper.readTree(body.getValue());

            assertThat(parsed.path("productId").asLong()).isEqualTo(7L);
            assertThat(parsed.path("variantId").asLong()).isEqualTo(9L);
            assertThat(parsed.path("quantity").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("clearCart takes no arguments")
        void clearCart() {
            when(toolExecutor.clearCart()).thenReturn("{\"cleared\":true}");

            assertThat(toolExecutionService.executeTool("clearCart", empty())).isEqualTo("{\"cleared\":true}");

            verify(toolExecutor).clearCart();
        }
    }

    @Nested
    @DisplayName("Order and payment tools")
    class OrderAndPaymentTools {

        @Test
        @DisplayName("placeOrder forwards the model's arguments verbatim")
        void placeOrderForwardsVerbatim() {
            when(toolExecutor.placeOrder("{\"addressId\":5}")).thenReturn("{\"orderId\":1001}");

            assertThat(toolExecutionService.executeTool("placeOrder", input("{\"addressId\":5}")))
                    .isEqualTo("{\"orderId\":1001}");

            verify(toolExecutor).placeOrder("{\"addressId\":5}");
        }

        @Test
        @DisplayName("buyNow forwards the model's arguments verbatim")
        void buyNowForwardsVerbatim() {
            String args = "{\"productId\":7,\"variantId\":9,\"quantity\":1,\"addressId\":5}";
            when(toolExecutor.buyNow(args)).thenReturn("{\"orderId\":1002}");

            assertThat(toolExecutionService.executeTool("buyNow", input(args)))
                    .isEqualTo("{\"orderId\":1002}");

            verify(toolExecutor).buyNow(args);
        }

        @Test
        @DisplayName("getOrder converts the id to a long")
        void getOrder() {
            when(toolExecutor.getOrder(1001L)).thenReturn("{\"status\":\"CONFIRMED\"}");

            assertThat(toolExecutionService.executeTool("getOrder", input("{\"orderId\":1001}")))
                    .isEqualTo("{\"status\":\"CONFIRMED\"}");

            verify(toolExecutor).getOrder(1001L);
        }

        @Test
        @DisplayName("getMyOrders takes no arguments")
        void getMyOrders() {
            when(toolExecutor.getMyOrders()).thenReturn("[]");

            assertThat(toolExecutionService.executeTool("getMyOrders", empty())).isEqualTo("[]");

            verify(toolExecutor).getMyOrders();
        }

        @Test
        @DisplayName("initiatePayment forwards the model's arguments verbatim")
        void initiatePayment() {
            String args = "{\"orderId\":1001,\"amount\":8999}";
            when(toolExecutor.initiatePayment(args)).thenReturn("{\"paymentId\":\"pay_1\"}");

            assertThat(toolExecutionService.executeTool("initiatePayment", input(args)))
                    .isEqualTo("{\"paymentId\":\"pay_1\"}");

            verify(toolExecutor).initiatePayment(args);
        }

        @Test
        @DisplayName("getAllAddresses takes no arguments")
        void getAllAddresses() {
            when(toolExecutor.getAllAddresses()).thenReturn("[]");

            assertThat(toolExecutionService.executeTool("getAllAddresses", empty())).isEqualTo("[]");

            verify(toolExecutor).getAllAddresses();
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class FailureHandling {

        @Test
        @DisplayName("an unknown tool never reaches the executor and comes back as an error payload")
        void anUnknownToolIsRejected() throws Exception {
            String result = toolExecutionService.executeTool("deleteAllProducts", empty());

            verifyNoInteractions(toolExecutor);

            JsonNode parsed = objectMapper.readTree(result);
            assertThat(parsed.path("error").asBoolean()).isTrue();
            assertThat(parsed.path("message").asText()).isEqualTo("Unknown tool: deleteAllProducts");
        }

        @Test
        @DisplayName("the error payload uses the exact marker the orchestrator looks for")
        void theErrorPayloadUsesTheAgreedMarker() {
            assertThat(toolExecutionService.executeTool("nope", empty())).contains("\"error\": true");
        }

        @Test
        @DisplayName("an exception thrown downstream becomes an error payload, never a 500")
        void anExceptionBecomesAnErrorPayload() throws Exception {
            when(toolExecutor.getCart()).thenThrow(new IllegalStateException("cart-service is down"));

            String result = toolExecutionService.executeTool("getCart", empty());

            JsonNode parsed = objectMapper.readTree(result);
            assertThat(parsed.path("error").asBoolean()).isTrue();
            assertThat(parsed.path("tool").asText()).isEqualTo("getCart");
            assertThat(parsed.path("message").asText()).isEqualTo("cart-service is down");
        }

        @Test
        @DisplayName("a null tool name is treated as unknown rather than blowing up")
        void aNullToolNameIsTreatedAsUnknown() {
            assertThat(toolExecutionService.executeTool(null, empty())).contains("\"error\": true");
        }

        @Test
        @DisplayName("malformed arguments degrade to zero rather than throwing")
        void malformedArgumentsDegradeToZero() {
            when(toolExecutor.getProductDetails(0L)).thenReturn("{}");

            toolExecutionService.executeTool("getProductDetails", input("{\"productId\":\"not-a-number\"}"));

            verify(toolExecutor).getProductDetails(0L);
        }
    }
}

