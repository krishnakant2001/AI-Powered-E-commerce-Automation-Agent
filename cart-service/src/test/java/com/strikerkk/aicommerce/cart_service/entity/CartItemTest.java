package com.strikerkk.aicommerce.cart_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CartItem entity")
class CartItemTest {

    private Field field(String name) throws NoSuchFieldException {
        return CartItem.class.getDeclaredField(name);
    }

    @Test
    @DisplayName("the builder populates every field, snapshots included")
    void builderPopulatesEveryField() {
        Cart cart = Cart.builder().id(7L).userId(42L).build();

        CartItem item = CartItem.builder()
                .id(500L)
                .cart(cart)
                .productId(1L)
                .variantId(2L)
                .quantity(3)
                .productName("JBL Flip 6")
                .productBrand("JBL")
                .priceAtAdd(new BigDecimal("8999.00"))
                .ProductImageUrl("products/1/primary.png")
                .size("M")
                .color("Black")
                .build();

        assertThat(item.getId()).isEqualTo(500L);
        assertThat(item.getCart()).isSameAs(cart);
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getVariantId()).isEqualTo(2L);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getProductName()).isEqualTo("JBL Flip 6");
        assertThat(item.getProductBrand()).isEqualTo("JBL");
        assertThat(item.getPriceAtAdd()).isEqualByComparingTo("8999.00");
        assertThat(item.getProductImageUrl()).isEqualTo("products/1/primary.png");
        assertThat(item.getSize()).isEqualTo("M");
        assertThat(item.getColor()).isEqualTo("Black");
    }

    @Test
    @DisplayName("the no-args constructor is present for JPA")
    void hasANoArgsConstructor() {
        CartItem item = new CartItem();

        assertThat(item.getId()).isNull();
        assertThat(item.getQuantity()).isNull();
        assertThat(item.getPriceAtAdd()).isNull();
    }

    @Test
    @DisplayName("the all-args constructor keeps the declaration order")
    void allArgsConstructorOrder() {
        Cart cart = Cart.builder().id(7L).build();

        CartItem item = new CartItem(
                500L, cart, 1L, 2L, 3,
                "JBL Flip 6", "JBL", new BigDecimal("8999.00"),
                "products/1/primary.png", "M", "Black");

        assertThat(item.getId()).isEqualTo(500L);
        assertThat(item.getCart()).isSameAs(cart);
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getVariantId()).isEqualTo(2L);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getProductName()).isEqualTo("JBL Flip 6");
        assertThat(item.getProductBrand()).isEqualTo("JBL");
        assertThat(item.getPriceAtAdd()).isEqualByComparingTo("8999.00");
        assertThat(item.getProductImageUrl()).isEqualTo("products/1/primary.png");
        assertThat(item.getSize()).isEqualTo("M");
        assertThat(item.getColor()).isEqualTo("Black");
    }

    @Test
    @DisplayName("every setter works")
    void settersWork() {
        CartItem item = new CartItem();
        Cart cart = Cart.builder().id(7L).build();

        item.setId(500L);
        item.setCart(cart);
        item.setProductId(1L);
        item.setVariantId(2L);
        item.setQuantity(3);
        item.setProductName("JBL Flip 6");
        item.setProductBrand("JBL");
        item.setPriceAtAdd(new BigDecimal("8999.00"));
        item.setProductImageUrl("products/1/primary.png");
        item.setSize("M");
        item.setColor("Black");

        assertThat(item.getCart()).isSameAs(cart);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getProductImageUrl()).isEqualTo("products/1/primary.png");
    }

    @Test
    @DisplayName("the price snapshot keeps its scale - it is the price the customer agreed to")
    void priceKeepsItsScale() {
        CartItem item = CartItem.builder().priceAtAdd(new BigDecimal("8999.00")).build();

        assertThat(item.getPriceAtAdd().scale()).isEqualTo(2);
        assertThat(item.getPriceAtAdd()).hasToString("8999.00");
    }

    // mapping ------------------------------------------------------------------

    @Test
    @DisplayName("is mapped to the 'cart_item' table")
    void isMappedToTheCartItemTable() {
        assertThat(CartItem.class.getAnnotation(Entity.class)).isNotNull();
        assertThat(CartItem.class.getAnnotation(Table.class).name()).isEqualTo("cart_item");
    }

    @Test
    @DisplayName("a product variant can only appear once per cart")
    void hasTheUniqueConstraint() {
        UniqueConstraint[] constraints = CartItem.class.getAnnotation(Table.class).uniqueConstraints();

        assertThat(constraints).hasSize(1);
        assertThat(constraints[0].name()).isEqualTo("uk_cart_product_variant");
        assertThat(constraints[0].columnNames())
                .containsExactly("cart_id", "product_id", "variant_id");
    }

    @Test
    @DisplayName("the id is generated by the database identity column")
    void idIsAnIdentityColumn() throws Exception {
        Field id = field("id");

        assertThat(id.getAnnotation(Id.class)).isNotNull();
        assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
    }

    @Test
    @DisplayName("the owning cart is fetched lazily and is mandatory")
    void cartIsLazyAndMandatory() throws Exception {
        Field cart = field("cart");

        assertThat(cart.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);
        assertThat(cart.getAnnotation(JoinColumn.class).name()).isEqualTo("cart_id");
        assertThat(cart.getAnnotation(JoinColumn.class).nullable()).isFalse();
    }

    @Test
    @DisplayName("every snapshot column is mandatory - the cart must render without product-service")
    void everySnapshotColumnIsMandatory() throws Exception {
        for (String name : new String[]{
                "productId", "variantId", "quantity",
                "productName", "productBrand", "priceAtAdd", "ProductImageUrl", "size", "color"}) {
            assertThat(field(name).getAnnotation(Column.class).nullable())
                    .describedAs("%s must be NOT NULL", name)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("price_at_add is a DECIMAL(10,2) - no floating point money")
    void priceIsADecimal() throws Exception {
        Column column = field("priceAtAdd").getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo("price_at_add");
        assertThat(column.precision()).isEqualTo(10);
        assertThat(column.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("the snake_case column names are explicit")
    void columnNamesAreExplicit() throws Exception {
        assertThat(field("productId").getAnnotation(Column.class).name()).isEqualTo("product_id");
        assertThat(field("variantId").getAnnotation(Column.class).name()).isEqualTo("variant_id");
        assertThat(field("quantity").getAnnotation(Column.class).name()).isEqualTo("quantity");
        assertThat(field("productName").getAnnotation(Column.class).name()).isEqualTo("product_name");
        assertThat(field("productBrand").getAnnotation(Column.class).name()).isEqualTo("product_brand");
        assertThat(field("ProductImageUrl").getAnnotation(Column.class).name()).isEqualTo("product_image_url");
        assertThat(field("size").getAnnotation(Column.class).name()).isEqualTo("size");
        assertThat(field("color").getAnnotation(Column.class).name()).isEqualTo("color");
    }
}

