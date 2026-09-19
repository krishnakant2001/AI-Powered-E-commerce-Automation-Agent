package com.strikerkk.aicommerce.cart_service.service;

import com.strikerkk.aicommerce.cart_service.auth.UserContext;
import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.response.CartItemResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.CartResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import com.strikerkk.aicommerce.cart_service.entity.Cart;
import com.strikerkk.aicommerce.cart_service.entity.CartItem;
import com.strikerkk.aicommerce.cart_service.exception.AccessDeniedException;
import com.strikerkk.aicommerce.cart_service.exception.IllegalStateException;
import com.strikerkk.aicommerce.cart_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.cart_service.repository.CartItemRepository;
import com.strikerkk.aicommerce.cart_service.repository.CartRepository;
import com.strikerkk.aicommerce.cart_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService")
class CartServiceTest {

    private static final Long USER_ID = TestDataFactory.USER_ID;
    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;
    private static final Long CART_ITEM_ID = TestDataFactory.CART_ITEM_ID;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartResilience4j cartResilience4j;

    @Captor
    private ArgumentCaptor<Cart> cartCaptor;

    @Captor
    private ArgumentCaptor<CartItem> itemCaptor;

    private CartService cartService;

    private Cart cart;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, cartResilience4j, new ModelMapper());
        cart = TestDataFactory.cart();
        UserContext.setUserId(String.valueOf(USER_ID));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void cartExists() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
    }

    // allCartItems ===================================================================

    @Nested
    @DisplayName("allCartItems")
    class AllCartItems {

        @Test
        @DisplayName("creates a cart the first time the customer opens it")
        void createsTheCartOnFirstAccess() {
            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CartResponse response = cartService.allCartItems();

            verify(cartRepository).save(cartCaptor.capture());
            assertThat(cartCaptor.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(cartCaptor.getValue().getCartItems()).isEmpty();
            assertThat(response.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("reuses the existing cart instead of creating a second one")
        void reusesTheExistingCart() {
            cartExists();

            CartResponse response = cartService.allCartItems();

            verify(cartRepository, never()).save(any(Cart.class));
            assertThat(response.getCartId()).isEqualTo(TestDataFactory.CART_ID);
        }

        @Test
        @DisplayName("an empty cart totals zero")
        void anEmptyCartTotalsZero() {
            cartExists();

            CartResponse response = cartService.allCartItems();

            assertThat(response.getItems()).isEmpty();
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTotalItems()).isZero();
            assertThat(response.getTotalUniqueItems()).isZero();
            assertThat(response.isEnriched()).isFalse();
            verifyNoInteractions(cartResilience4j);
        }

        @Test
        @DisplayName("prices each line as priceAtAdd x quantity")
        void pricesEachLine() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 3, new BigDecimal("8999.00"));
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse());

            CartResponse response = cartService.allCartItems();

            assertThat(response.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getItemTotal()).isEqualByComparingTo("26997.00"));
        }

        @Test
        @DisplayName("sums the amount, the quantities and the distinct lines")
        void sumsTheTotals() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 2, new BigDecimal("100.00"));
            TestDataFactory.attachItem(cart, 2L, 3L, 4L, 3, new BigDecimal("10.00"));
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("100.00"), true, true));
            when(cartResilience4j.getItemDetails(3L, 4L))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("10.00"), true, true));

            CartResponse response = cartService.allCartItems();

            assertThat(response.getTotalAmount()).isEqualByComparingTo("230.00");
            assertThat(response.getTotalItems()).isEqualTo(5);
            assertThat(response.getTotalUniqueItems()).isEqualTo(2);
        }

        @Test
        @DisplayName("fills productDetails, variantName included")
        void fillsTheProductDetails() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, TestDataFactory.PRICE);
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse());

            CartItemResponse item = cartService.allCartItems().getItems().get(0);

            assertThat(item.getProductDetails()).isNotNull();
            assertThat(item.getProductDetails().getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(item.getProductDetails().getProductBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(item.getProductDetails().getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
            assertThat(item.getProductDetails().getVariantName()).isEqualTo("M - Black");
        }

        @Test
        @DisplayName("refreshes a stale price and reports enriched=true")
        void refreshesAStalePrice() {
            CartItem item = TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 2, new BigDecimal("100.00"));
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("120.00"), true, true));

            CartResponse response = cartService.allCartItems();

            assertThat(item.getPriceAtAdd()).isEqualByComparingTo("120.00");
            assertThat(response.getTotalAmount()).isEqualByComparingTo("240.00");
            assertThat(response.isEnriched()).isTrue();
        }

        @Test
        @DisplayName("an unchanged price leaves enriched=false")
        void anUnchangedPriceLeavesEnrichedFalse() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, new BigDecimal("100.00"));
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("100.00"), true, true));

            assertThat(cartService.allCartItems().isEnriched()).isFalse();
        }

        @Test
        @DisplayName("a different scale counts as a price change (documents the equals/compareTo bug)")
        void aDifferentScaleCountsAsAChange() {
            CartItem item = TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, new BigDecimal("100.00"));
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("100.0"), true, true));

            CartResponse response = cartService.allCartItems();

            assertThat(response.isEnriched()).isTrue();
            assertThat(item.getPriceAtAdd()).hasToString("100.0");
        }

        @Test
        @DisplayName("drops a line whose product is no longer available and reports enriched=true")
        void dropsAnUnavailableLine() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, TestDataFactory.PRICE);
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(TestDataFactory.PRICE, false, true));

            CartResponse response = cartService.allCartItems();

            assertThat(cart.getCartItems()).isEmpty();
            assertThat(response.getItems()).isEmpty();
            assertThat(response.isEnriched()).isTrue();
        }

        @Test
        @DisplayName("an out of stock line is kept - only an unavailable product is dropped")
        void keepsAnOutOfStockLine() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, TestDataFactory.PRICE);
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(TestDataFactory.PRICE, true, false));

            assertThat(cartService.allCartItems().getItems()).hasSize(1);
        }

        @Test
        @DisplayName("drops several lines at once without a ConcurrentModificationException")
        void dropsSeveralLinesAtOnce() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, TestDataFactory.PRICE);
            TestDataFactory.attachItem(cart, 2L, 3L, 4L, 1, TestDataFactory.PRICE);
            cartExists();
            when(cartResilience4j.getItemDetails(anyLong(), anyLong()))
                    .thenReturn(TestDataFactory.productCartResponse(TestDataFactory.PRICE, false, true));

            CartResponse response = cartService.allCartItems();

            assertThat(cart.getCartItems()).isEmpty();
            assertThat(response.getItems()).isEmpty();
        }

        @Test
        @DisplayName("keeps the stale snapshot when product-service is down - the cart still renders")
        void keepsTheStaleSnapshotWhenProductServiceIsDown() {
            CartItem item = TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 2, new BigDecimal("100.00"));
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenThrow(new RuntimeException("Product Service unavailable"));

            CartResponse response = cartService.allCartItems();

            assertThat(item.getPriceAtAdd()).isEqualByComparingTo("100.00");
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("200.00");
            assertThat(response.isEnriched()).isFalse();
        }

        @Test
        @DisplayName("one unreachable line does not stop the others from being refreshed")
        void oneFailureDoesNotStopTheOthers() {
            CartItem refreshed = TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, new BigDecimal("100.00"));
            CartItem stale = TestDataFactory.attachItem(cart, 2L, 3L, 4L, 1, new BigDecimal("50.00"));
            cartExists();
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenThrow(new RuntimeException("down"));
            when(cartResilience4j.getItemDetails(3L, 4L))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("55.00"), true, true));

            CartResponse response = cartService.allCartItems();

            assertThat(refreshed.getPriceAtAdd()).isEqualByComparingTo("100.00");
            assertThat(stale.getPriceAtAdd()).isEqualByComparingTo("55.00");
            assertThat(response.isEnriched()).isTrue();
        }

        @Test
        @DisplayName("copies the audit timestamps of the cart")
        void copiesTheTimestamps() {
            cart.setCreatedAt(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));
            cart.setUpdatedAt(java.time.LocalDateTime.of(2026, 1, 2, 10, 0));
            cartExists();

            CartResponse response = cartService.allCartItems();

            assertThat(response.getCreatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));
            assertThat(response.getUpdatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 2, 10, 0));
        }

        @Test
        @DisplayName("an anonymous caller fails with a NumberFormatException (rendered as a 500)")
        void anAnonymousCallerFails() {
            UserContext.clear();

            assertThatThrownBy(() -> cartService.allCartItems())
                    .isInstanceOf(NumberFormatException.class);
            verifyNoInteractions(cartRepository);
        }

        @Test
        @DisplayName("a non numeric caller id fails with a NumberFormatException")
        void aNonNumericCallerIdFails() {
            UserContext.setUserId("not-a-number");

            assertThatThrownBy(() -> cartService.allCartItems())
                    .isInstanceOf(NumberFormatException.class);
        }
    }

    // addItemToCart ===================================================================

    @Nested
    @DisplayName("addItemToCart")
    class AddItem {

        @Test
        @DisplayName("snapshots the product data of a brand new line")
        void snapshotsTheProductData() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), PRODUCT_ID, VARIANT_ID))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse());

            cartService.addItemToCart(TestDataFactory.addRequest(PRODUCT_ID, VARIANT_ID, 2));

            verify(cartRepository).save(cartCaptor.capture());
            assertThat(cartCaptor.getValue().getCartItems()).singleElement().satisfies(item -> {
                assertThat(item.getCart()).isSameAs(cart);
                assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
                assertThat(item.getVariantId()).isEqualTo(VARIANT_ID);
                assertThat(item.getQuantity()).isEqualTo(2);
                assertThat(item.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
                assertThat(item.getProductBrand()).isEqualTo(TestDataFactory.BRAND);
                assertThat(item.getPriceAtAdd()).isEqualByComparingTo(TestDataFactory.PRICE);
                assertThat(item.getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
                assertThat(item.getSize()).isEqualTo(TestDataFactory.SIZE);
                assertThat(item.getColor()).isEqualTo(TestDataFactory.COLOR);
            });
        }

        @Test
        @DisplayName("the price always comes from product-service, never from the client")
        void thePriceComesFromProductService() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(anyLong(), anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("1.00"), true, true));

            CartResponse response = cartService.addItemToCart(TestDataFactory.addRequest(PRODUCT_ID, VARIANT_ID, 1));

            assertThat(response.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getPriceAtAdd()).isEqualByComparingTo("1.00"));
        }

        @Test
        @DisplayName("creates the cart when the customer has none yet")
        void createsTheCartWhenThereIsNone() {
            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(any(), anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse());

            CartResponse response = cartService.addItemToCart(TestDataFactory.addRequest());

            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("adding the same variant again only bumps the quantity")
        void bumpsTheQuantityOfAnExistingLine() {
            CartItem existing = TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 2, new BigDecimal("100.00"));
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), PRODUCT_ID, VARIANT_ID))
                    .thenReturn(Optional.of(existing));

            CartResponse response = cartService.addItemToCart(TestDataFactory.addRequest(PRODUCT_ID, VARIANT_ID, 3));

            assertThat(existing.getQuantity()).isEqualTo(5);
            verify(cartItemRepository).save(existing);
            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(response.getTotalItems()).isEqualTo(5);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("bumping an existing line never calls product-service, so the price stays frozen")
        void bumpingDoesNotRepriceTheLine() {
            CartItem existing = TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, new BigDecimal("100.00"));
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), PRODUCT_ID, VARIANT_ID))
                    .thenReturn(Optional.of(existing));

            cartService.addItemToCart(TestDataFactory.addRequest(PRODUCT_ID, VARIANT_ID, 1));

            verifyNoInteractions(cartResilience4j);
            verify(cartRepository, never()).save(any(Cart.class));
            assertThat(existing.getPriceAtAdd()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("refuses an out of stock variant and writes nothing")
        void refusesAnOutOfStockVariant() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), PRODUCT_ID, VARIANT_ID))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(TestDataFactory.PRICE, true, false));

            assertThatThrownBy(() -> cartService.addItemToCart(TestDataFactory.addRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Product variant is out of stock. productId=1, variantId=2");

            verify(cartRepository, never()).save(any(Cart.class));
            verify(cartItemRepository, never()).save(any(CartItem.class));
            assertThat(cart.getCartItems()).isEmpty();
        }

        @Test
        @DisplayName("an unavailable but in-stock product is still accepted (documents the gap)")
        void acceptsAnUnavailableButInStockProduct() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), PRODUCT_ID, VARIANT_ID))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse(TestDataFactory.PRICE, false, true));

            assertThat(cartService.addItemToCart(TestDataFactory.addRequest()).getItems()).hasSize(1);
        }

        @Test
        @DisplayName("propagates the failure when product-service is unreachable")
        void propagatesTheProductServiceFailure() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), PRODUCT_ID, VARIANT_ID))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenThrow(new RuntimeException("Product Service unavailable"));

            assertThatThrownBy(() -> cartService.addItemToCart(TestDataFactory.addRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Product Service unavailable");

            verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("the lookup is scoped by cart, product and variant")
        void theLookupIsScoped() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), 7L, 8L))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(7L, 8L)).thenReturn(TestDataFactory.productCartResponse());

            cartService.addItemToCart(TestDataFactory.addRequest(7L, 8L, 1));

            verify(cartItemRepository).findByCartIdAndProductIdAndVariantId(cart.getId(), 7L, 8L);
        }

        @Test
        @DisplayName("a second variant of the same product becomes its own line")
        void asecondVariantIsItsOwnLine() {
            TestDataFactory.attachItem(cart, 1L, PRODUCT_ID, VARIANT_ID, 1, new BigDecimal("100.00"));
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), PRODUCT_ID, 9L))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, 9L))
                    .thenReturn(TestDataFactory.productCartResponse(new BigDecimal("100.00"), true, true));

            CartResponse response = cartService.addItemToCart(TestDataFactory.addRequest(PRODUCT_ID, 9L, 1));

            assertThat(response.getTotalUniqueItems()).isEqualTo(2);
        }

        @Test
        @DisplayName("the answer is never flagged as enriched - no price refresh happens here")
        void theAnswerIsNeverEnriched() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(anyLong(), anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productCartResponse());

            assertThat(cartService.addItemToCart(TestDataFactory.addRequest()).isEnriched()).isFalse();
        }

        @Test
        @DisplayName("a payload without the inStock flag blows up with a NullPointerException")
        void aMissingInStockFlagBlowsUp() {
            cartExists();
            when(cartItemRepository.findByCartIdAndProductIdAndVariantId(anyLong(), anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            ProductCartResponse withoutFlag = TestDataFactory.productCartResponse();
            withoutFlag.setInStock(null);
            when(cartResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).thenReturn(withoutFlag);

            assertThatThrownBy(() -> cartService.addItemToCart(TestDataFactory.addRequest()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("an anonymous caller fails before anything is read")
        void anAnonymousCallerFails() {
            UserContext.clear();

            assertThatThrownBy(() -> cartService.addItemToCart(TestDataFactory.addRequest()))
                    .isInstanceOf(NumberFormatException.class);
            verifyNoInteractions(cartRepository, cartItemRepository, cartResilience4j);
        }
    }

    // updateCartItem ===================================================================

    @Nested
    @DisplayName("updateCartItem")
    class UpdateItem {

        @Test
        @DisplayName("overwrites the quantity and persists it")
        void overwritesTheQuantity() {
            CartItem item = TestDataFactory.cartItem(cart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            cartService.updateCartItem(TestDataFactory.updateRequest(4), CART_ITEM_ID);

            verify(cartItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(4);
        }

        @Test
        @DisplayName("the new quantity replaces the old one, it is not added to it")
        void replacesRatherThanAdds() {
            CartItem item = TestDataFactory.cartItem(cart, 10);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            assertThat(cartService.updateCartItem(TestDataFactory.updateRequest(2), CART_ITEM_ID).getQuantity())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("recomputes the line total")
        void recomputesTheLineTotal() {
            CartItem item = TestDataFactory.cartItem(500L, cart, PRODUCT_ID, VARIANT_ID, 1, new BigDecimal("100.00"));
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            CartItemResponse response = cartService.updateCartItem(TestDataFactory.updateRequest(3), CART_ITEM_ID);

            assertThat(response.getItemTotal()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("returns the full line, product details included")
        void returnsTheFullLine() {
            CartItem item = TestDataFactory.cartItem(cart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            CartItemResponse response = cartService.updateCartItem(TestDataFactory.updateRequest(2), CART_ITEM_ID);

            assertThat(response.getId()).isEqualTo(CART_ITEM_ID);
            assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(response.getVariantId()).isEqualTo(VARIANT_ID);
            assertThat(response.getPriceAtAdd()).isEqualByComparingTo(TestDataFactory.PRICE);
            assertThat(response.getProductDetails()).isNotNull();
            assertThat(response.getProductDetails().getVariantName()).isEqualTo("M - Black");
            assertThat(response.getProductDetails().getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
        }

        @Test
        @DisplayName("an unknown line is a 404")
        void anUnknownLineIsNotFound() {
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateCartItem(TestDataFactory.updateRequest(2), CART_ITEM_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Cart Item is not found");

            verify(cartItemRepository, never()).save(any(CartItem.class));
        }

        @Test
        @DisplayName("never calls product-service - the price snapshot is untouched")
        void doesNotCallProductService() {
            CartItem item = TestDataFactory.cartItem(cart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            cartService.updateCartItem(TestDataFactory.updateRequest(2), CART_ITEM_ID);

            verifyNoInteractions(cartResilience4j);
            assertThat(item.getPriceAtAdd()).isEqualByComparingTo(TestDataFactory.PRICE);
        }

        @Test
        @DisplayName("updates the line of ANOTHER customer (documents the missing ownership check)")
        void doesNotCheckTheOwnership() {
            Cart foreignCart = TestDataFactory.cart(8L, TestDataFactory.OTHER_USER_ID);
            CartItem foreignItem = TestDataFactory.cartItem(foreignCart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(foreignItem));

            CartItemResponse response = cartService.updateCartItem(TestDataFactory.updateRequest(9), CART_ITEM_ID);

            assertThat(response.getQuantity()).isEqualTo(9);
            assertThat(foreignItem.getQuantity()).isEqualTo(9);
            verify(cartItemRepository).save(foreignItem);
        }

        @Test
        @DisplayName("does not even read the caller id")
        void doesNotReadTheCaller() {
            UserContext.clear();
            CartItem item = TestDataFactory.cartItem(cart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            assertThat(cartService.updateCartItem(TestDataFactory.updateRequest(2), CART_ITEM_ID)).isNotNull();
        }
    }

    // deleteItemFromCart ===================================================================

    @Nested
    @DisplayName("deleteItemFromCart")
    class DeleteItem {

        @Test
        @DisplayName("removes the line of its owner")
        void removesTheLine() {
            CartItem item = TestDataFactory.cartItem(cart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            cartService.deleteItemFromCart(CART_ITEM_ID);

            verify(cartItemRepository).delete(item);
        }

        @Test
        @DisplayName("an unknown line is a 404 that names the id")
        void anUnknownLineIsNotFound() {
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.deleteItemFromCart(CART_ITEM_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Cart item not found 500");

            verify(cartItemRepository, never()).delete(any(CartItem.class));
        }

        @Test
        @DisplayName("refuses to delete the line of another customer")
        void refusesAForeignLine() {
            Cart foreignCart = TestDataFactory.cart(8L, TestDataFactory.OTHER_USER_ID);
            CartItem foreignItem = TestDataFactory.cartItem(foreignCart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(foreignItem));

            assertThatThrownBy(() -> cartService.deleteItemFromCart(CART_ITEM_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Unauthorized request");

            verify(cartItemRepository, never()).delete(any(CartItem.class));
        }

        @Test
        @DisplayName("the ownership is compared by value, not by reference")
        void comparesTheOwnerByValue() {
            Cart sameOwnerOtherInstance = TestDataFactory.cart(8L, Long.valueOf("" + USER_ID));
            CartItem item = TestDataFactory.cartItem(sameOwnerOtherInstance, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            cartService.deleteItemFromCart(CART_ITEM_ID);

            verify(cartItemRepository).delete(item);
        }

        @Test
        @DisplayName("never deletes the cart itself")
        void neverDeletesTheCart() {
            CartItem item = TestDataFactory.cartItem(cart, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            cartService.deleteItemFromCart(CART_ITEM_ID);

            verify(cartRepository, never()).delete(any(Cart.class));
            verify(cartRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("an anonymous caller fails before the line is read")
        void anAnonymousCallerFails() {
            UserContext.clear();

            assertThatThrownBy(() -> cartService.deleteItemFromCart(CART_ITEM_ID))
                    .isInstanceOf(NumberFormatException.class);
            verifyNoInteractions(cartItemRepository);
        }
    }

    // clearCart ===================================================================

    @Nested
    @DisplayName("clearCart")
    class ClearCart {

        @Test
        @DisplayName("removes every line of the cart of the caller")
        void removesEveryLine() {
            cartExists();

            cartService.clearCart();

            verify(cartItemRepository).deleteAllByCartId(TestDataFactory.CART_ID);
        }

        @Test
        @DisplayName("keeps the cart row - only the lines are deleted")
        void keepsTheCartRow() {
            cartExists();

            cartService.clearCart();

            verify(cartRepository, never()).delete(any(Cart.class));
            verify(cartRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("a customer without a cart gets a 404")
        void aCustomerWithoutACartGetsA404() {
            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.clearCart())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("No cart found for userId 42");

            verify(cartItemRepository, never()).deleteAllByCartId(anyLong());
        }

        @Test
        @DisplayName("never creates a cart on the way - unlike the read path")
        void neverCreatesACart() {
            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.clearCart()).isInstanceOf(ResourceNotFoundException.class);

            verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("is scoped to the caller")
        void isScopedToTheCaller() {
            cartExists();

            cartService.clearCart();

            verify(cartRepository).findByUserId(USER_ID);
        }

        @Test
        @DisplayName("clearing an already empty cart is a no-op that still succeeds")
        void clearingAnEmptyCartSucceeds() {
            cartExists();

            cartService.clearCart();

            verify(cartItemRepository).deleteAllByCartId(TestDataFactory.CART_ID);
        }

        @Test
        @DisplayName("an anonymous caller fails before anything is read")
        void anAnonymousCallerFails() {
            UserContext.clear();

            assertThatThrownBy(() -> cartService.clearCart()).isInstanceOf(NumberFormatException.class);
            verifyNoInteractions(cartRepository, cartItemRepository);
        }
    }
}

