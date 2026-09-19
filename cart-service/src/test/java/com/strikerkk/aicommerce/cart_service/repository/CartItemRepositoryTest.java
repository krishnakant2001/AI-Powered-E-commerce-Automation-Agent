package com.strikerkk.aicommerce.cart_service.repository;

import com.strikerkk.aicommerce.cart_service.entity.Cart;
import com.strikerkk.aicommerce.cart_service.entity.CartItem;
import org.junit.jupiter.api.BeforeEach;
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
@DisplayName("CartItemRepository")
class CartItemRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cart cart;
    private Cart otherCart;

    @BeforeEach
    void setUp() {
        cart = cartRepository.saveAndFlush(Cart.builder().userId(42L).build());
        otherCart = cartRepository.saveAndFlush(Cart.builder().userId(99L).build());
    }

    private CartItem item(Cart owner, Long productId, Long variantId, int quantity) {
        return CartItem.builder()
                .cart(owner)
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
    @DisplayName("persists an item and generates its id")
    void shouldPersistAnItem() {
        CartItem saved = cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("keeps the price snapshot with its scale")
    void keepsThePriceSnapshot() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 1));
        entityManager.clear();

        assertThat(cartItemRepository.findAll().get(0).getPriceAtAdd()).isEqualByComparingTo("8999.00");
    }

    // findByCartIdAndProductIdAndVariantId ------------------------------------------------------------------

    @Test
    @DisplayName("finds the item of a cart by its product and variant")
    void shouldFindTheItem() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));

        Optional<CartItem> found = cartItemRepository
                .findByCartIdAndProductIdAndVariantId(cart.getId(), 1L, 2L);

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("is empty when the product is not in that cart")
    void isEmptyForAnUnknownProduct() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));

        assertThat(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), 777L, 2L)).isEmpty();
    }

    @Test
    @DisplayName("another variant of the same product is a different item")
    void anotherVariantIsADifferentItem() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));

        assertThat(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), 1L, 2L)).isPresent();
        assertThat(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), 1L, 3L)).isEmpty();
    }

    @Test
    @DisplayName("never reaches into the cart of somebody else")
    void isScopedToTheCart() {
        cartItemRepository.saveAndFlush(item(otherCart, 1L, 2L, 3));

        assertThat(cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), 1L, 2L)).isEmpty();
        assertThat(cartItemRepository.findByCartIdAndProductIdAndVariantId(otherCart.getId(), 1L, 2L)).isPresent();
    }

    // constraints ------------------------------------------------------------------

    @Test
    @DisplayName("a product variant can only appear once per cart")
    void theSameVariantCannotBeAddedTwice() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the same variant may sit in two different carts")
    void theSameVariantMaySitInTwoCarts() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));
        cartItemRepository.saveAndFlush(item(otherCart, 1L, 2L, 1));

        assertThat(cartItemRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("an item always belongs to a cart")
    void cartIsMandatory() {
        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(item(null, 1L, 2L, 1)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("the snapshot columns are mandatory")
    void theSnapshotsAreMandatory() {
        CartItem withoutName = item(cart, 1L, 2L, 1);
        withoutName.setProductName(null);

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(withoutName))
                .isInstanceOf(Exception.class);
    }

    // deleteAllByCartId ------------------------------------------------------------------

    @Test
    @DisplayName("deleteAllByCartId empties that cart")
    void deleteAllByCartIdEmptiesTheCart() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));
        cartItemRepository.saveAndFlush(item(cart, 3L, 4L, 1));

        cartItemRepository.deleteAllByCartId(cart.getId());
        cartItemRepository.flush();
        entityManager.clear();

        assertThat(cartItemRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("deleteAllByCartId never touches the cart of somebody else")
    void deleteAllByCartIdIsScoped() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));
        cartItemRepository.saveAndFlush(item(otherCart, 1L, 2L, 1));

        cartItemRepository.deleteAllByCartId(cart.getId());
        cartItemRepository.flush();
        entityManager.clear();

        assertThat(cartItemRepository.findAll())
                .hasSize(1)
                .allSatisfy(remaining -> assertThat(remaining.getCart().getId()).isEqualTo(otherCart.getId()));
    }

    @Test
    @DisplayName("deleteAllByCartId keeps the cart row itself - only the items go")
    void deleteAllByCartIdKeepsTheCart() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));

        cartItemRepository.deleteAllByCartId(cart.getId());
        cartItemRepository.flush();
        entityManager.clear();

        assertThat(cartRepository.findById(cart.getId())).isPresent();
    }

    @Test
    @DisplayName("deleteAllByCartId on an empty cart is a no-op")
    void deleteAllByCartIdOnAnEmptyCartIsANoOp() {
        cartItemRepository.deleteAllByCartId(cart.getId());
        cartItemRepository.flush();

        assertThat(cartItemRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("deleteAllByCartId for an unknown cart is a no-op")
    void deleteAllByCartIdForAnUnknownCartIsANoOp() {
        cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 3));

        cartItemRepository.deleteAllByCartId(123_456L);
        cartItemRepository.flush();

        assertThat(cartItemRepository.findAll()).hasSize(1);
    }

    // plain CRUD ------------------------------------------------------------------

    @Test
    @DisplayName("an updated quantity is written back")
    void updatesTheQuantity() {
        CartItem saved = cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 1));

        saved.setQuantity(5);
        cartItemRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(cartItemRepository.findById(saved.getId())).get()
                .extracting(CartItem::getQuantity).isEqualTo(5);
    }

    @Test
    @DisplayName("deleting an item leaves the cart and the other items alone")
    void deletingAnItemLeavesTheRestAlone() {
        CartItem first = cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 1));
        cartItemRepository.saveAndFlush(item(cart, 3L, 4L, 1));

        cartItemRepository.delete(first);
        cartItemRepository.flush();
        entityManager.clear();

        assertThat(cartItemRepository.findAll()).hasSize(1);
        assertThat(cartRepository.findById(cart.getId())).isPresent();
    }

    @Test
    @DisplayName("the owning cart is reachable from the item")
    void theOwnerIsReachable() {
        CartItem saved = cartItemRepository.saveAndFlush(item(cart, 1L, 2L, 1));
        entityManager.clear();

        assertThat(cartItemRepository.findById(saved.getId())).get()
                .satisfies(found -> assertThat(found.getCart().getUserId()).isEqualTo(42L));
    }
}

