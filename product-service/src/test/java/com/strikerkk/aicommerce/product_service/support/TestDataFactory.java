package com.strikerkk.aicommerce.product_service.support;

import com.strikerkk.aicommerce.order_service.event.OrderConfirmedEvent;
import com.strikerkk.aicommerce.order_service.event.OrderPlacedItem;
import com.strikerkk.aicommerce.product_service.dto.clientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.product_service.dto.request.ProductImageRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductVariantRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductImageResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductVariantResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;

public final class TestDataFactory {

    public static final Long PRODUCT_ID = 1L;
    public static final Long VARIANT_ID = 100L;
    public static final Long IMAGE_ID = 200L;

    /** The admin that owns every fixture produced by this factory. */
    public static final String ADMIN_ID = "admin-1";
    /** A second admin, used to assert the ownership rules. */
    public static final String OTHER_ADMIN_ID = "admin-2";

    public static final String PRODUCT_NAME = "JBL Flip 6";
    public static final String BRAND = "JBL";
    public static final String CATEGORY = "Speakers";
    public static final BigDecimal PRICE = new BigDecimal("8999.00");
    public static final BigDecimal PRICE_OVERRIDE = new BigDecimal("9499.00");
    public static final String S3_KEY = "products/1/8f1b2c3d-flip6.png";

    private TestDataFactory() {
    }

    // entities ------------------------------------------------------------------

    public static Product product() {
        return product(PRODUCT_ID, ADMIN_ID);
    }

    public static Product product(Long id, String createdBy) {
        return Product.builder()
                .id(id)
                .name(PRODUCT_NAME)
                .brand(BRAND)
                .description("Portable waterproof bluetooth speaker")
                .price(PRICE)
                .category(CATEGORY)
                .stockCount(25)
                .isAvailable(true)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now().minusDays(3))
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    public static Product transientProduct() {
        return Product.builder()
                .name(PRODUCT_NAME)
                .brand(BRAND)
                .description("Portable waterproof bluetooth speaker")
                .price(PRICE)
                .category(CATEGORY)
                .stockCount(25)
                .isAvailable(true)
                .createdBy(ADMIN_ID)
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    public static ProductVariant variant(Long id, Product product) {
        return variant(id, product, 10);
    }

    public static ProductVariant variant(Long id, Product product, int stockCount) {
        return ProductVariant.builder()
                .id(id)
                .product(product)
                .size("M")
                .color("Black")
                .stockCount(stockCount)
                .priceOverride(PRICE_OVERRIDE)
                .build();
    }

    public static ProductImage image(Long id, Product product, boolean isPrimary) {
        return image(id, product, isPrimary, S3_KEY);
    }

    public static ProductImage image(Long id, Product product, boolean isPrimary, String key) {
        return ProductImage.builder()
                .id(id)
                .product(product)
                .url(key)
                .isPrimary(isPrimary)
                .build();
    }

    // requests ------------------------------------------------------------------

    public static ProductRequest productRequest() {
        ProductRequest request = new ProductRequest();
        request.setName(PRODUCT_NAME);
        request.setBrand(BRAND);
        request.setDescription("Portable waterproof bluetooth speaker");
        request.setPrice(PRICE);
        request.setCategory(CATEGORY);
        request.setStockCount(25);
        request.setIsAvailable(true);
        return request;
    }

    public static ProductVariantRequest productVariantRequest() {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setSize("M");
        request.setColor("Black");
        request.setStockCount(10);
        request.setPriceOverride(PRICE_OVERRIDE);
        return request;
    }

    public static ProductImageRequest productImageRequest(boolean isPrimary) {
        return new ProductImageRequest(multipartFile(), isPrimary);
    }

    public static MockMultipartFile multipartFile() {
        return new MockMultipartFile(
                "image", "flip6.png", "image/png", "binary-image-content".getBytes());
    }

    public static MockMultipartFile emptyMultipartFile() {
        return new MockMultipartFile("image", "flip6.png", "image/png", new byte[0]);
    }

    // responses -----------------------------------------------------------------

    public static ProductResponse productResponse() {
        ProductResponse response = new ProductResponse();
        response.setId(PRODUCT_ID);
        response.setName(PRODUCT_NAME);
        response.setBrand(BRAND);
        response.setDescription("Portable waterproof bluetooth speaker");
        response.setPrice(PRICE);
        response.setCategory(CATEGORY);
        response.setStockCount(25);
        response.setIsAvailable(true);
        response.setCreatedAt(LocalDateTime.now().minusDays(3));
        response.setVariants(new ArrayList<>());
        response.setImages(new ArrayList<>());
        return response;
    }

    public static ProductVariantResponse productVariantResponse() {
        ProductVariantResponse response = new ProductVariantResponse();
        response.setId(VARIANT_ID);
        response.setSize("M");
        response.setColor("Black");
        response.setStockCount(10);
        response.setPriceOverride(PRICE_OVERRIDE);
        return response;
    }

    public static ProductImageResponse productImageResponse(boolean isPrimary) {
        ProductImageResponse response = new ProductImageResponse();
        response.setId(IMAGE_ID);
        response.setImageUrl(S3_KEY);
        response.setIsPrimary(isPrimary);
        return response;
    }

    public static ProductItemResponse productItemResponse() {
        return ProductItemResponse.builder()
                .productId(PRODUCT_ID)
                .productName(PRODUCT_NAME)
                .brandName(BRAND)
                .price(PRICE_OVERRIDE)
                .isAvailable(true)
                .variantId(VARIANT_ID)
                .size("M")
                .color("Black")
                .inStock(true)
                .imageUrl(S3_KEY)
                .build();
    }

    // events --------------------------------------------------------------------

    public static OrderPlacedItem orderPlacedItem(Long productId, Long variantId, int quantity) {
        OrderPlacedItem item = new OrderPlacedItem();
        item.setId(1L);
        item.setProductId(productId);
        item.setVariantId(variantId);
        item.setQuantity(quantity);
        return item;
    }

    public static OrderConfirmedEvent orderConfirmedEvent(OrderPlacedItem... items) {
        OrderConfirmedEvent event = new OrderConfirmedEvent();
        event.setOrderId(500L);
        event.setOrderPlacedItems(new ArrayList<>(Arrays.asList(items)));
        return event;
    }
}



