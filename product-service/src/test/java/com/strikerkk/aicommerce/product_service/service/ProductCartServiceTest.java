package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.dto.clientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCartService")
class ProductCartServiceTest {

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;

    @Mock
    private ProductVariantRepository variantRepository;

    @InjectMocks
    private ProductCartService productCartService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = TestDataFactory.product();
    }

    private ProductVariant variantOf(Product parent, int stockCount) {
        ProductVariant variant = TestDataFactory.variant(VARIANT_ID, parent, stockCount);
        parent.getVariants().add(variant);
        return variant;
    }

    @Test
    @DisplayName("flattens the product, the variant and the primary image into one payload")
    void shouldBuildTheItemPayload() {
        ProductVariant variant = variantOf(product, 5);
        product.getImages().add(TestDataFactory.image(1L, product, false, "products/1/secondary.png"));
        product.getImages().add(TestDataFactory.image(2L, product, true, "products/1/primary.png"));

        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        ProductItemResponse response = productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID);

        assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(response.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
        assertThat(response.getBrandName()).isEqualTo(TestDataFactory.BRAND);
        assertThat(response.getIsAvailable()).isTrue();
        assertThat(response.getVariantId()).isEqualTo(VARIANT_ID);
        assertThat(response.getSize()).isEqualTo("M");
        assertThat(response.getColor()).isEqualTo("Black");
        assertThat(response.getImageUrl()).isEqualTo("products/1/primary.png");
    }

    @Test
    @DisplayName("prices the item with the variant override, not with the base product price")
    void shouldUseTheVariantPrice() {
        ProductVariant variant = variantOf(product, 5);

        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        ProductItemResponse response = productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID);

        assertThat(response.getPrice()).isEqualByComparingTo(TestDataFactory.PRICE_OVERRIDE);
        assertThat(response.getPrice()).isNotEqualByComparingTo(TestDataFactory.PRICE);
    }

    @Test
    @DisplayName("reports inStock=true while the variant still has stock")
    void shouldReportInStock() {
        ProductVariant variant = variantOf(product, 1);
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getInStock()).isTrue();
    }

    @Test
    @DisplayName("reports inStock=false once the variant stock reaches zero")
    void shouldReportOutOfStock() {
        ProductVariant variant = variantOf(product, 0);
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getInStock()).isFalse();
    }

    @Test
    @DisplayName("reports inStock=false for a negative stock count")
    void shouldReportOutOfStockForNegativeStock() {
        ProductVariant variant = variantOf(product, -2);
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getInStock()).isFalse();
    }

    @Test
    @DisplayName("returns the first primary image when several are flagged")
    void shouldReturnTheFirstPrimaryImage() {
        ProductVariant variant = variantOf(product, 5);
        product.getImages().add(TestDataFactory.image(1L, product, true, "products/1/first.png"));
        product.getImages().add(TestDataFactory.image(2L, product, true, "products/1/second.png"));

        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getImageUrl())
                .isEqualTo("products/1/first.png");
    }

    @Test
    @DisplayName("returns a null image url when none of the images is primary")
    void shouldReturnNullWhenNoPrimaryImage() {
        ProductVariant variant = variantOf(product, 5);
        product.getImages().add(TestDataFactory.image(1L, product, false, "products/1/a.png"));

        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getImageUrl()).isNull();
    }

    @Test
    @DisplayName("returns a null image url when the product has no image at all")
    void shouldReturnNullWhenNoImages() {
        Product bare = TestDataFactory.product();
        bare.setImages(new ArrayList<>());
        ProductVariant variant = TestDataFactory.variant(VARIANT_ID, bare, 5);

        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getImageUrl()).isNull();
    }

    @Test
    @DisplayName("propagates the isAvailable flag of the parent product")
    void shouldPropagateAvailability() {
        product.setIsAvailable(false);
        ProductVariant variant = variantOf(product, 5);

        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getIsAvailable()).isFalse();
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when the variant does not belong to the product")
    void shouldThrowWhenVariantMissing() {
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product variant is not found");
    }

    @Test
    @DisplayName("scopes the lookup by both the variant id and the product id")
    void shouldScopeTheLookupByBothIds() {
        ProductVariant variant = variantOf(product, 5);
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID);

        verify(variantRepository).findByIdAndProductId(VARIANT_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("keeps the images of another product out of the payload")
    void shouldNotLeakForeignImages() {
        Product other = TestDataFactory.product(2L, TestDataFactory.OTHER_ADMIN_ID);
        List<ProductImage> otherImages = other.getImages();
        otherImages.add(TestDataFactory.image(9L, other, true, "products/2/primary.png"));

        ProductVariant variant = variantOf(product, 5);
        product.getImages().add(TestDataFactory.image(1L, product, true, "products/1/primary.png"));

        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        assertThat(productCartService.getProductItemDetails(PRODUCT_ID, VARIANT_ID).getImageUrl())
                .isEqualTo("products/1/primary.png");
    }
}

