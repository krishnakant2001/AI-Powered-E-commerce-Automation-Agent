package com.strikerkk.aicommerce.agent_service.llm;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolDefinitionBuilder {

    public List<Object> build() {
        return List.of(
                buildTool("searchProducts", "Search for products by name or category",
                        buildSchema(
                                prop("search", "string", "Product name to search for, e.g. 'Apple Watch Series 10'"),
                                prop("category", "string", "Product category filter, e.g. 'electroincs'")
                        ),
                        List.of()
                ),

                buildTool("getProductDetails", "Get full details of a product including all variants and images",
                        buildSchema(
                                prop("productId", "number", "The numeric product ID")
                        ),
                        List.of("productId")
                ),

                buildTool("getVariantInfo", "Check stock availability and price for a specific product variant",
                        buildSchema(
                                prop("productId", "number", "The product ID"),
                                prop("variantId", "number", "The variant ID to check")
                        ),
                        List.of("productId", "variantId")
                ),

                buildTool("getCart", "Get all items currently in the user's cart",
                        buildSchema(),
                        List.of()
                ),

                buildTool("addToCart", "Add a product variant to the user's cart",
                        buildSchema(
                                prop("productId", "number", "The product ID"),
                                prop("variantId", "number", "The variant ID"),
                                prop("quantity", "number", "Quantity to add")
                        ),
                        List.of("productId", "variantId", "quantity")
                ),

                buildTool("clearCart", "Clear all items from the user's cart",
                        buildSchema(),
                        List.of()
                ),

                buildTool("buyNow", "Place an order immediately without using the cart. Use this for single-item orders.",
                        buildSchema(
                                prop("productId", "number", "The product ID"),
                                prop("variantId", "number", "The variant ID"),
                                prop("quantity", "number", "Quantity to order"),
                                prop("addressId", "number", "The delivery address ID")
                        ),
                        List.of("productId", "variantId", "quantity", "addressId")
                ),

                buildTool("placeOrder", "Place an order from the items in the cart",
                        buildSchema(prop("addressId", "number", "The delivery address ID")),
                        List.of("addressId")
                ),

                buildTool("getOrder",
                        "Get the status and details of a specific order",
                        buildSchema(prop("orderId", "number", "The order ID")),
                        List.of("orderId")
                ),

                buildTool("getMyOrders", "Get all orders placed by the user",
                        buildSchema(),
                        List.of()
                ),

                buildTool("initiatePayment", "Initiate payment for an order. Returns a Razorpay payment ID.",
                        buildSchema(
                                prop("orderId", "number", "The order ID to pay for"),
                                prop("amount", "number", "The payment amount")
                        ),
                        List.of("orderId", "amount")
                ),

                buildTool("getAllAddresses", "Get all saved delivery addresses for the user",
                        buildSchema(),
                        List.of()
                )
        );
    }

    private Map<String, Object> buildTool(String name, String description, Map<String, Object> schema,
                                          List<String> required) {
        return new HashMap<String, Object>() {
            {
                put("name", name);
                put("description", description);
                put("input schema", new HashMap<String, Object>(){
                    {
                        put("type", "object");
                        put("properties", schema);
                        put("required", required);
                    }
                });
            }
        };
    }

    @SafeVarargs
    private Map<String, Object> buildSchema(Map.Entry<String, Object> ...props) {
        Map<String, Object> schema = new LinkedHashMap<>();
        for(Map.Entry<String, Object> prop : props) {
            schema.put(prop.getKey(), prop.getValue());
        }
        return schema;
    }

    private Map<String, Object> buildSchema() {
        return new LinkedHashMap<>();
    }

    private Map.Entry<String, Object> prop(String name, String type, String description) {
        return Map.entry(name,new HashMap<>() {
            {
                put("type", type);
                put("description", description);
            }
        });
    }
}
