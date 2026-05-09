package com.strikerkk.aicommerce.agent_service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolExecutionService {

    private final ToolExecutor toolExecutor;

    public String executeTool(String toolName, JsonNode input) {
        try {
            return switch (toolName) {

                case "searchProducts" -> toolExecutor.searchProducts(
                       input.path("search").asText(null),
                       input.path("category").asText(null)
                );
                case "getProductDetails" -> toolExecutor.getProductDetails(
                       input.path("productId").asLong()
                );
                case "getProductItemDetails" -> toolExecutor.getProductItemDetails(
                       input.path("productId").asLong(),
                       input.path("variantId").asLong()
                );


                case "getCart" -> toolExecutor.getCart();
                case "addToCart" -> toolExecutor.addToCart(buildCartRequestBody(input));
                case "clearCart" -> toolExecutor.clearCart();


                case "placeOrder" -> toolExecutor.placeOrder(input.toString());
                case "buyNow" -> toolExecutor.buyNow(input.toString());
                case "getOrder" -> toolExecutor.getOrder(
                       input.path("orderId").asLong()
                );
                case "getMyOrders" -> toolExecutor.getMyOrders();


                case "initiatePayment" -> toolExecutor.initiatePayment(input.toString());
                case "getAllAddresses" -> toolExecutor.getAllAddresses();

                default -> {
                   log.warn("Unknown tool called: {}", toolName);
                   yield String.format(
                           "{\"error\": true, \"message\": \"Unknown tool: %s\"}", toolName
                   );
                }
            };
        } catch (Exception e) {
           log.error("Tool execution failed for tool={}: {}", toolName, e.getMessage());
           return String.format(
                   "{\"error\": true, \"tool\": \"%s\", \"message\": \"%s\"}", toolName, e.getMessage()
           );
        }
    }

    private String buildCartRequestBody(JsonNode input) {
        return String.format(
                "{\"productId\":%d,\"variantId\":%d,\"quantity\":%d}",
                input.path("productId").asLong(),
                input.path("variantId").asLong(),
                input.path("quantity").asInt(1)
        );
    }
}
