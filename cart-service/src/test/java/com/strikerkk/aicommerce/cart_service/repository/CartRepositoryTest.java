package com.strikerkk.aicommerce.cart_service.repository;

import com.strikerkk.aicommerce.cart_service.entity.Cart;
import com.strikerkk.aicommerce.cart_service.entity.CartItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("CartRepository")
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cart cart(Long userId) {
        return Cart.builder().userId(userId).build();
    }

    private CartItem item(Cart cart, Long productId, Long variantId, int quantity) {
        return CartItem.builder()
                .cart(cart)
                .productId(productId)
                .variantId(variantId)
                .quantity(quantity)
                .productName("JBL Flip 6")
                .productBrand("JBL")
                .priceAtAdd(new BigDecimal("8999.00"))
                .ProductImageUrl("products/1/primary.png")
                .size("M")
                .color("Black")
                .build();
    }

    @Test
    @DisplayName("persists a cart and generates its id")
    void shouldPersistACart() {
        Cart saved = cartRepository.saveAndFlush(cart(42L));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("stamps created_at and updated_at through the Hibernate callbacks")
    void shouldStampTheTimestamps() {
        Cart saved = cartRepository.saveAndFlush(cart(42L));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("a brand new cart starts with an empty item list")
    void aNewCartIsEmpty() {
        Cart saved = cartRepository.saveAndFlush(cart(42L));

        entityManager.clear();

        assertThat(cartRepository.findById(saved.getId()))
                .get()
                .satisfies(found -> assertThat(found.getCartItems()).isEmpty());
    }

    @Test
    @DisplayName("findByUserId returns the cart of that customer")
    void findByUserIdReturnsTheCart() {
        cartRepository.saveAndFlush(cart(42L));

        Optional<Cart> found = cartRepository.findByUserId(42L);

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("findByUserId is empty for a customer without a cart")
    void findByUserIdIsEmptyForAnUnknownCustomer() {
        assertThat(cartRepository.findByUserId(999L)).isEmpty();
    }

    @Test
    @DisplayName("findByUserId never returns the cart of somebody else")
    void findByUserIdIsScopedToTheOwner() {
        cartRepository.saveAndFlush(cart(42L));
        cartRepository.saveAndFlush(cart(99L));

        assertThat(cartRepository.findByUserId(42L)).get()
                .extracting(Cart::getUserId).isEqualTo(42L);
        assertThat(cartRepository.findByUserId(99L)).get()
                .extracting(Cart::getUserId).isEqualTo(99L);
    }

    @Test
    @DisplayName("a customer can only ever own one cart")
    void userIdIsUnique() {
        cartRepository.saveAndFlush(cart(42L));

        assertThatThrownBy(() -> cartRepository.saveAndFlush(cart(42L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("user_id is mandatory")
    void userIdIsMandatory() {
        assertThatThrownBy(() -> cartRepository.saveAndFlush(cart(null)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("saving the cart cascades the new items")
    void savingTheCartCascadesTheItems() {
        Cart cart = cart(42L);
        cart.getCartItems().add(item(cart, 1L, 2L, 3));

        Cart saved = cartRepository.saveAndFlush(cart);
        entityManager.clear();

        assertThat(cartItemRepository.findAll()).hasSize(1);
        assertThat(cartRepository.findById(saved.getId())).get()
                .satisfies(found -> assertThat(found.getCartItems()).hasSize(1));
    }

    @Test
    @DisplayName("removing an item from the list deletes its row (orphanRemoval)")
    void removingAnItemDeletesTheRow() {
        Cart cart = cart(42L);
        cart.getCartItems().add(item(cart, 1L, 2L, 3));
        Cart saved = cartRepository.saveAndFlush(cart);
        entityManager.clear();

        Cart managed = cartRepository.findById(saved.getId()).orElseThrow();
        managed.getCartItems().clear();
        cartRepository.saveAndFlush(managed);
        entityManager.clear();

        assertThat(cartItemRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("deleting the cart cascades the deletion of its items")
    void deletingTheCartDeletesTheItems() {
        Cart cart = cart(42L);
        cart.getCartItems().add(item(cart, 1L, 2L, 3));
        cart.getCartItems().add(item(cart, 3L, 4L, 1));
        Cart saved = cartRepository.saveAndFlush(cart);
        entityManager.clear();

        cartRepository.delete(cartRepository.findById(saved.getId()).orElseThrow());
        cartRepository.flush();
        entityManager.clear();

        assertThat(cartItemRepository.findAll()).isEmpty();
        assertThat(cartRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("updated_at moves forward when the cart is modified")
    void updatedAtMovesForward() throws Exception {
        Cart saved = cartRepository.saveAndFlush(cart(42L));
        var firstUpdate = saved.getUpdatedAt();

        Thread.sleep(20);

        saved.getCartItems().add(item(saved, 1L, 2L, 1));
        Cart updated = cartRepository.saveAndFlush(saved);

        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(firstUpdate);
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("deleting a cart that does not exist is a no-op through deleteById")
    void deletingAnUnknownCartIsSafe() {
        assertThat(cartRepository.findById(123_456L)).isEmpty();
        cartRepository.deleteById(123_456L);
        cartRepository.flush();
    }
}

