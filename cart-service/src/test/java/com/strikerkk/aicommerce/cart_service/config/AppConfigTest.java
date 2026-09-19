package com.strikerkk.aicommerce.cart_service.config;

import com.strikerkk.aicommerce.cart_service.dto.response.CartItemResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductDetails;
import com.strikerkk.aicommerce.cart_service.entity.Cart;
import com.strikerkk.aicommerce.cart_service.entity.CartItem;
import com.strikerkk.aicommerce.cart_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppConfig / ModelMapper")
class AppConfigTest {

    private final ModelMapper modelMapper = new AppConfig().modelMapper();

    @Test
    @DisplayName("the factory method returns a usable ModelMapper")
    void producesAModelMapper() {
        assertThat(modelMapper).isNotNull();
    }

    @Test
    @DisplayName("a new instance is produced per call - Spring turns it into a singleton bean")
    void producesANewInstancePerCall() {
        assertThat(new AppConfig().modelMapper()).isNotSameAs(new AppConfig().modelMapper());
    }

    @Nested
    @DisplayName("CartItem -> CartItemResponse")
    class ToCartItemResponse {

        @Test
        @DisplayName("copies the id, the ids of the product and of the variant, the quantity and the price")
        void copiesTheScalarFields() {
            CartItem item = TestDataFactory.cartItem(500L, null, 1L, 2L, 3, new BigDecimal("8999.00"));

            CartItemResponse response = modelMapper.map(item, CartItemResponse.class);

            assertThat(response.getId()).isEqualTo(500L);
            assertThat(response.getProductId()).isEqualTo(1L);
            assertThat(response.getVariantId()).isEqualTo(2L);
            assertThat(response.getQuantity()).isEqualTo(3);
            assertThat(response.getPriceAtAdd()).isEqualByComparingTo("8999.00");
        }

        @Test
        @DisplayName("leaves itemTotal null - the service computes it")
        void doesNotComputeTheItemTotal() {
            CartItemResponse response = modelMapper.map(TestDataFactory.cartItem(), CartItemResponse.class);

            assertThat(response.getItemTotal()).isNull();
        }

        @Test
        @DisplayName("leaves productDetails null - the service builds it separately")
        void doesNotBuildTheProductDetails() {
            CartItemResponse response = modelMapper.map(TestDataFactory.cartItem(), CartItemResponse.class);

            assertThat(response.getProductDetails()).isNull();
        }

        @Test
        @DisplayName("never leaks the owning cart into the payload")
        void doesNotLeakTheCart() {
            Cart cart = TestDataFactory.cart();
            CartItem item = TestDataFactory.cartItem(cart, 1);

            CartItemResponse response = modelMapper.map(item, CartItemResponse.class);

            assertThat(response.getId()).isEqualTo(TestDataFactory.CART_ITEM_ID);
            assertThat(response.toString()).doesNotContain("userId");
        }

        @Test
        @DisplayName("the price snapshot keeps its scale")
        void keepsThePriceScale() {
            CartItem item = TestDataFactory.cartItem(500L, null, 1L, 2L, 1, new BigDecimal("1499.50"));

            assertThat(modelMapper.map(item, CartItemResponse.class).getPriceAtAdd())
                    .hasToString("1499.50");
        }
    }

    @Nested
    @DisplayName("CartItem -> ProductDetails")
    class ToProductDetails {

        @Test
        @DisplayName("copies the product name, the brand, the size and the colour")
        void copiesTheSnapshots() {
            ProductDetails details = modelMapper.map(TestDataFactory.cartItem(), ProductDetails.class);

            assertThat(details.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(details.getProductBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(details.getSize()).isEqualTo(TestDataFactory.SIZE);
            assertThat(details.getColor()).isEqualTo(TestDataFactory.COLOR);
        }

        @Test
        @DisplayName("copies the image url even though the entity field starts with a capital letter")
        void copiesTheImageUrl() {
            ProductDetails details = modelMapper.map(TestDataFactory.cartItem(), ProductDetails.class);

            assertThat(details.getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
        }

        @Test
        @DisplayName("leaves variantName null - the service concatenates size and colour itself")
        void doesNotBuildTheVariantName() {
            ProductDetails details = modelMapper.map(TestDataFactory.cartItem(), ProductDetails.class);

            assertThat(details.getVariantName()).isNull();
        }

        @Test
        @DisplayName("variantSku is always null - the cart never snapshots it")
        void variantSkuIsAlwaysNull() {
            ProductDetails details = modelMapper.map(TestDataFactory.cartItem(), ProductDetails.class);

            assertThat(details.getVariantSku()).isNull();
        }

        @Test
        @DisplayName("a null snapshot stays null instead of blowing up")
        void toleratesNullSnapshots() {
            CartItem item = new CartItem();
            item.setProductId(1L);
            item.setVariantId(2L);

            ProductDetails details = modelMapper.map(item, ProductDetails.class);

            assertThat(details.getProductName()).isNull();
            assertThat(details.getProductBrand()).isNull();
            assertThat(details.getSize()).isNull();
            assertThat(details.getColor()).isNull();
        }
    }
}

